/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpSpanStatusExtractorTest {
  @Mock private HttpServerAttributesGetter<Map<String, String>, Map<String, String>> serverGetter;

  @Mock private HttpClientAttributesGetter<Map<String, String>, Map<String, String>> clientGetter;

  @Mock private SpanStatusBuilder spanStatusBuilder;

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 500, 501, 600, 601})
  void hasServerStatus(int statusCode) {
    StatusCode expectedStatusCode = HttpStatusConverter.SERVER.statusFromHttpStatus(statusCode);
    when(serverGetter.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    HttpSpanStatusExtractor.create(serverGetter)
        .extract(spanStatusBuilder, Collections.emptyMap(), Collections.emptyMap(), null);

    if (expectedStatusCode != StatusCode.UNSET) {
      verify(spanStatusBuilder).setStatus(expectedStatusCode);
    } else {
      verifyNoInteractions(spanStatusBuilder);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasClientStatus(int statusCode) {
    StatusCode expectedStatusCode = HttpStatusConverter.CLIENT.statusFromHttpStatus(statusCode);
    when(clientGetter.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    HttpSpanStatusExtractor.create(clientGetter)
        .extract(spanStatusBuilder, Collections.emptyMap(), Collections.emptyMap(), null);

    if (expectedStatusCode != StatusCode.UNSET) {
      verify(spanStatusBuilder).setStatus(expectedStatusCode);
    } else {
      verifyNoInteractions(spanStatusBuilder);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasServerStatusAndException(int statusCode) {
    StatusCode expectedStatusCode = HttpStatusConverter.SERVER.statusFromHttpStatus(statusCode);
    when(serverGetter.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    // Presence of exception has no effect.
    HttpSpanStatusExtractor.create(serverGetter)
        .extract(
            spanStatusBuilder,
            Collections.emptyMap(),
            Collections.emptyMap(),
            new IllegalStateException("test"));

    if (expectedStatusCode == StatusCode.ERROR) {
      verify(spanStatusBuilder).setStatus(expectedStatusCode);
    } else {
      verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasClientStatusAndException(int statusCode) {
    StatusCode expectedStatusCode = HttpStatusConverter.CLIENT.statusFromHttpStatus(statusCode);
    when(clientGetter.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    // Presence of exception has no effect.
    HttpSpanStatusExtractor.create(clientGetter)
        .extract(
            spanStatusBuilder,
            Collections.emptyMap(),
            Collections.emptyMap(),
            new IllegalStateException("test"));

    if (expectedStatusCode == StatusCode.ERROR) {
      verify(spanStatusBuilder).setStatus(expectedStatusCode);
    } else {
      verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
    }
  }

  @Test
  void hasNoServerStatus_fallsBackToDefault_unset() {
    when(serverGetter.statusCode(anyMap(), anyMap())).thenReturn(null);

    HttpSpanStatusExtractor.create(serverGetter)
        .extract(spanStatusBuilder, Collections.emptyMap(), Collections.emptyMap(), null);

    verifyNoInteractions(spanStatusBuilder);
  }

  @Test
  void hasNoClientStatus_fallsBackToDefault_unset() {
    when(clientGetter.statusCode(anyMap(), anyMap())).thenReturn(null);

    HttpSpanStatusExtractor.create(clientGetter)
        .extract(spanStatusBuilder, Collections.emptyMap(), Collections.emptyMap(), null);

    verifyNoInteractions(spanStatusBuilder);
  }

  @Test
  void hasNoServerStatus_fallsBackToDefault_error() {
    when(serverGetter.statusCode(anyMap(), anyMap())).thenReturn(null);

    HttpSpanStatusExtractor.create(serverGetter)
        .extract(
            spanStatusBuilder,
            Collections.emptyMap(),
            Collections.emptyMap(),
            new IllegalStateException("test"));
    verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
  }

  @Test
  void hasNoClientStatus_fallsBackToDefault_error() {
    when(clientGetter.statusCode(anyMap(), anyMap())).thenReturn(null);

    HttpSpanStatusExtractor.create(clientGetter)
        .extract(
            spanStatusBuilder,
            Collections.emptyMap(),
            Collections.emptyMap(),
            new IllegalStateException("test"));
    verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
  }
}
