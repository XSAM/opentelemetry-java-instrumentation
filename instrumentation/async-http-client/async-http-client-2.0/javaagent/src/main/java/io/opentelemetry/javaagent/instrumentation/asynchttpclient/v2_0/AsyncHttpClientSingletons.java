/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import org.asynchttpclient.Response;

public final class AsyncHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.async-http-client-2.0";

  private static final Instrumenter<RequestContext, Response> INSTRUMENTER;

  static {
    AsyncHttpClientHttpAttributesGetter httpAttributesGetter =
        new AsyncHttpClientHttpAttributesGetter();
    AsyncHttpClientNetAttributesGetter netAttributeGetter =
        new AsyncHttpClientNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<RequestContext, Response>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                    .build())
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributeGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    netAttributeGetter, CommonConfig.get().getPeerServiceMapping()))
            .addAttributesExtractor(new AsyncHttpClientAdditionalAttributesExtractor())
            .addOperationMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<RequestContext, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private AsyncHttpClientSingletons() {}
}
