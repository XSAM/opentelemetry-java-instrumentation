/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.springframework.web.servlet.ModelAndView;

public final class SpringWebMvcSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webmvc-3.1";

  private static final Instrumenter<Object, Void> HANDLER_INSTRUMENTER;

  private static final Instrumenter<ModelAndView, Void> MODEL_AND_VIEW_INSTRUMENTER;

  static {
    HANDLER_INSTRUMENTER =
        Instrumenter.<Object, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, new HandlerSpanNameExtractor())
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .newInstrumenter();

    MODEL_AND_VIEW_INSTRUMENTER =
        Instrumenter.<ModelAndView, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                new ModelAndViewSpanNameExtractor())
            .addAttributesExtractor(new ModelAndViewAttributesExtractor())
            .setEnabled(ExperimentalConfig.get().viewTelemetryEnabled())
            .newInstrumenter();
  }

  public static Instrumenter<Object, Void> handlerInstrumenter() {
    return HANDLER_INSTRUMENTER;
  }

  public static Instrumenter<ModelAndView, Void> modelAndViewInstrumenter() {
    return MODEL_AND_VIEW_INSTRUMENTER;
  }

  private SpringWebMvcSingletons() {}
}
