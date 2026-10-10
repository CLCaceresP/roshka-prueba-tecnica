package com.roshka.challenge.inventario.configuracion;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Conecta el appender de Logback con el {@link OpenTelemetry} que autoconfigura Spring Boot, para
 * que los logs salgan por el mismo exportador OTLP que las trazas (ADR 0016).
 */
@Component
class InstalarAppenderOpenTelemetry implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  InstalarAppenderOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterPropertiesSet() {
    OpenTelemetryAppender.install(openTelemetry);
  }
}
