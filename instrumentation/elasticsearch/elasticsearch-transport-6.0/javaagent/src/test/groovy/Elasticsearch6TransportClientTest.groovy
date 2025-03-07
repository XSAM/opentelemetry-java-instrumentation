/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.TransportAddress
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.node.Node
import org.elasticsearch.transport.RemoteTransportException
import org.elasticsearch.transport.TransportService
import org.elasticsearch.transport.client.PreBuiltTransportClient
import spock.lang.Shared
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING

class Elasticsearch6TransportClientTest extends AbstractElasticsearchTransportClientTest {
  public static final long TIMEOUT = 10000 // 10 seconds

  @Shared
  TransportAddress tcpPublishAddress
  @Shared
  Node testNode
  @Shared
  File esWorkingDir
  @Shared
  String clusterName = UUID.randomUUID().toString()

  @Shared
  TransportClient client

  def setupSpec() {
    esWorkingDir = File.createTempDir("test-es-working-dir-", "")
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
      .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
      .put("discovery.type", "single-node")
      .build()
    testNode = NodeFactory.newNode(settings)
    testNode.start()
    tcpPublishAddress = testNode.injector().getInstance(TransportService).boundAddress().publishAddress()

    client = new PreBuiltTransportClient(
      Settings.builder()
      // Since we use listeners to close spans this should make our span closing deterministic which is good for tests
        .put("thread_pool.listener.size", 1)
        .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
        .build()
    )
    client.addTransportAddress(tcpPublishAddress)
    runWithSpan("setup") {
      // this may potentially create multiple requests and therefore multiple spans, so we wrap this call
      // into a top level trace to get exactly one trace in the result.
      client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)
      // disable periodic refresh in InternalClusterInfoService as it creates spans that tests don't expect
      client.admin().cluster().updateSettings(new ClusterUpdateSettingsRequest().transientSettings(["cluster.routing.allocation.disk.threshold_enabled": false]))
    }
    waitForTraces(1)
  }

  def cleanupSpec() {
    client?.close()
    testNode?.close()
    if (esWorkingDir != null) {
      FileSystemUtils.deleteSubDirectories(esWorkingDir.toPath())
      esWorkingDir.delete()
    }
  }

  @Override
  TransportClient client() {
    client
  }

  @Unroll
  def "test elasticsearch status #callKind"() {
    setup:
    def clusterHealthStatus = runWithSpan("parent") {
      call.call()
    }

    expect:
    clusterHealthStatus.name() == "GREEN"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "ClusterHealthAction"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.NET_PEER_IP" tcpPublishAddress.address
            "$SemanticAttributes.NET_PEER_PORT" tcpPublishAddress.port
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "ClusterHealthAction"
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
          }
        }
        span(2) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }

    where:
    callKind | call
    "sync"   | { clusterHealthSync() }
    "async"  | { clusterHealthAsync() }
  }

  def "test elasticsearch error #callKind"() {
    when:
    runWithSpan("parent") {
      call.call(indexName, indexType, id)
    }

    then:
    thrown IndexNotFoundException

    and:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          status ERROR
          errorEvent IndexNotFoundException, ~/no such index( \[invalid-index])?/
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "GetAction"
          kind CLIENT
          status ERROR
          errorEvent RemoteTransportException, String
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "GetAction"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
          }
        }
        span(2) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }

    where:
    indexName = "invalid-index"
    indexType = "test-type"
    id = "1"
    callKind | call
    "sync" | { indexName, indexType, id -> prepareGetSync(indexName, indexType, id) }
    "async" | { indexName, indexType, id -> prepareGetAsync(indexName, indexType, id) }
  }

  def "test elasticsearch get"() {
    setup:
    def indexResult = client.admin().indices().prepareCreate(indexName).get()

    expect:
    indexResult.index() == indexName

    when:
    def emptyResult = client.prepareGet(indexName, indexType, id).get()

    then:
    !emptyResult.isExists()
    emptyResult.id == id
    emptyResult.type == indexType
    emptyResult.index == indexName

    when:
    def createResult = client.prepareIndex(indexName, indexType, id).setSource([:]).get()

    then:
    createResult.id == id
    createResult.type == indexType
    createResult.index == indexName
    createResult.status().status == 201

    when:
    def result = client.prepareGet(indexName, indexType, id).get()

    then:
    result.isExists()
    result.id == id
    result.type == indexType
    result.index == indexName

    and:
    assertTraces(5) {
      // PutMappingAction and IndexAction run in separate threads so their order can vary
      traces.subList(2, 4).sort(orderByRootSpanName(
        "PutMappingAction", // elasticsearch < 7
        "AutoPutMappingAction", // elasticsearch >= 7
        "IndexAction"))

      trace(0, 1) {
        span(0) {
          name "CreateIndexAction"
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_PEER_IP" tcpPublishAddress.address
            "$SemanticAttributes.NET_PEER_PORT" tcpPublishAddress.port
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "CreateIndexAction"
            "elasticsearch.action" "CreateIndexAction"
            "elasticsearch.request" "CreateIndexRequest"
            "elasticsearch.request.indices" indexName
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "GetAction"
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_PEER_IP" tcpPublishAddress.address
            "$SemanticAttributes.NET_PEER_PORT" tcpPublishAddress.port
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "GetAction"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" indexType
            "elasticsearch.id" "1"
            "elasticsearch.version"(-1)
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name ~/(Auto)?PutMappingAction/
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" ~/(Auto)?PutMappingAction/
            "elasticsearch.action" ~/(Auto)?PutMappingAction/
            "elasticsearch.request" "PutMappingRequest"
          }
        }
      }
      trace(3, 1) {
        span(0) {
          name "IndexAction"
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_PEER_IP" tcpPublishAddress.address
            "$SemanticAttributes.NET_PEER_PORT" tcpPublishAddress.port
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "IndexAction"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" indexType
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 201
            "elasticsearch.shard.replication.total" 2
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.failed" 0
          }
        }
      }
      trace(4, 1) {
        span(0) {
          name "GetAction"
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_PEER_IP" tcpPublishAddress.address
            "$SemanticAttributes.NET_PEER_PORT" tcpPublishAddress.port
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "GetAction"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" indexType
            "elasticsearch.id" "1"
            "elasticsearch.version" 1
          }
        }
      }
    }

    cleanup:
    client.admin().indices().prepareDelete(indexName).get()

    where:
    indexName = "test-index"
    indexType = "test-type"
    id = "1"
  }
}
