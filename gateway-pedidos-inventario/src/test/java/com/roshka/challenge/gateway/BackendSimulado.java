package com.roshka.challenge.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Una instancia de backend que responde con su nombre y los headers que reenvió el gateway. */
final class BackendSimulado implements AutoCloseable {

  private final HttpServer servidor;
  private final AtomicInteger aciertos = new AtomicInteger();

  BackendSimulado(String nombre) {
    try {
      servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    servidor.createContext(
        "/",
        intercambio -> {
          aciertos.incrementAndGet();
          var cabeceras = intercambio.getRequestHeaders();
          var cuerpo =
              "{\"backend\":\"%s\",\"path\":\"%s\",\"correlationId\":\"%s\",\"traceparent\":\"%s\",\"idempotencyKey\":\"%s\"}"
                  .formatted(
                      nombre,
                      intercambio.getRequestURI().getPath(),
                      cabeceras.getFirst("X-Correlation-Id"),
                      cabeceras.getFirst("traceparent"),
                      cabeceras.getFirst("Idempotency-Key"))
                  .getBytes(StandardCharsets.UTF_8);
          intercambio.getResponseHeaders().add("Content-Type", "application/json");
          var idCorrelacion = cabeceras.getFirst("X-Correlation-Id");
          if (idCorrelacion != null) {
            intercambio.getResponseHeaders().add("X-Correlation-Id", idCorrelacion);
          }
          intercambio.sendResponseHeaders(200, cuerpo.length);
          try (var out = intercambio.getResponseBody()) {
            out.write(cuerpo);
          }
        });
    servidor.start();
  }

  String url() {
    return "http://localhost:" + servidor.getAddress().getPort();
  }

  int aciertos() {
    return aciertos.get();
  }

  @Override
  public void close() {
    servidor.stop(0);
  }
}
