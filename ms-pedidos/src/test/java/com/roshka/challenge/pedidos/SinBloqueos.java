package com.roshka.challenge.pedidos;

import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoDistribuido;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reemplaza el bloqueo de Redisson por uno que siempre concede: demuestra que la corrección viene
 * de la base de datos (claves, bloqueo optimista) y que el bloqueo es solo defensa en profundidad
 * (ADR 0005).
 */
@TestConfiguration(proxyBeanMethods = false)
public class SinBloqueos {

  @Bean
  @Primary
  BloqueoDistribuido siempreConcedido() {
    return new BloqueoDistribuido() {
      @Override
      public <T> T llamarConBloqueo(
          String nombre, Duration espera, Duration lease, Supplier<T> accion) {
        return accion.get();
      }
    };
  }
}
