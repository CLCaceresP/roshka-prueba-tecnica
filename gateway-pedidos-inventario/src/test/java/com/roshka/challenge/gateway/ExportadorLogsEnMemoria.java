package com.roshka.challenge.gateway;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Sustituye al exportador OTLP en los tests: guarda en memoria los logs que se exportarían. */
public class ExportadorLogsEnMemoria implements LogRecordExporter {

  private final List<LogRecordData> registros = new CopyOnWriteArrayList<>();

  public List<LogRecordData> registros() {
    return registros;
  }

  public static String atributo(LogRecordData registro, String clave) {
    return registro.getAttributes().asMap().entrySet().stream()
        .filter(e -> e.getKey().getKey().equals(clave))
        .map(e -> String.valueOf(e.getValue()))
        .findFirst()
        .orElse("");
  }

  public static String recurso(LogRecordData registro, String clave) {
    return registro.getResource().getAttributes().asMap().entrySet().stream()
        .filter(e -> e.getKey().getKey().equals(clave))
        .map(e -> String.valueOf(e.getValue()))
        .findFirst()
        .orElse("");
  }

  @Override
  public CompletableResultCode export(Collection<LogRecordData> logs) {
    registros.addAll(logs);
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @TestConfiguration
  public static class Configuracion {

    @Bean
    ExportadorLogsEnMemoria exportadorLogsEnMemoria() {
      return new ExportadorLogsEnMemoria();
    }
  }
}
