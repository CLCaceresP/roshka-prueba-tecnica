package com.roshka.challenge.pedidos.adaptadores.salida.persistencia;

import com.roshka.challenge.pedidos.aplicacion.puertos.salida.MensajesProcesados;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMensajesProcesados implements MensajesProcesados {

  private final JdbcClient jdbc;
  private final Clock reloj;

  JdbcMensajesProcesados(JdbcClient jdbc, Clock reloj) {
    this.jdbc = jdbc;
    this.reloj = reloj;
  }

  /**
   * ON CONFLICT en lugar de capturar la violación de la clave primaria: en Postgres un error aborta
   * toda la transacción, y un duplicado concurrente se bloquea aquí hasta que el primero commitea.
   */
  @Override
  public boolean marcarProcesado(UUID idMensaje) {
    return jdbc.sql(
                """
                insert into processed_messages (message_id, processed_at) values (:id, :at)
                on conflict (message_id) do nothing
                """)
            .param("id", idMensaje)
            .param("at", OffsetDateTime.now(reloj))
            .update()
        == 1;
  }
}
