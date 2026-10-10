package com.roshka.challenge.pedidos.adaptadores.salida.persistencia;

import com.roshka.challenge.pedidos.aplicacion.idempotencia.RespuestaAlmacenada;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.AlmacenClavesIdempotencia;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcAlmacenClavesIdempotencia implements AlmacenClavesIdempotencia {

  private static final TypeReference<Map<String, String>> CABECERAS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  JdbcAlmacenClavesIdempotencia(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public Optional<ClaveRegistrada> buscarValido(String clave, Instant ahora) {
    return jdbc.sql(
            """
            select request_hash, response_code, response_headers, response_body
              from idempotency_keys
             where key = :key and expires_at > :now and response_code is not null
            """)
        .param("key", clave)
        .param("now", en(ahora))
        .query(
            (rs, n) ->
                new ClaveRegistrada(
                    rs.getString("request_hash"),
                    new RespuestaAlmacenada(
                        rs.getInt("response_code"),
                        json.readValue(rs.getString("response_headers"), CABECERAS),
                        rs.getString("response_body"))))
        .optional();
  }

  /**
   * Toma una clave vencida en el mismo lugar; una clave vigente de una solicitud ya commiteada no
   * se toca.
   */
  @Override
  public boolean reclamar(String clave, String huella, Instant ahora, Instant expiraEn) {
    return jdbc.sql(
                """
                insert into idempotency_keys (key, request_hash, created_at, expires_at)
                values (:key, :hash, :now, :expiresAt)
                on conflict (key) do update
                   set request_hash = excluded.request_hash,
                       response_code = null,
                       response_headers = null,
                       response_body = null,
                       created_at = excluded.created_at,
                       expires_at = excluded.expires_at
                 where idempotency_keys.expires_at <= :now
                """)
            .param("key", clave)
            .param("hash", huella)
            .param("now", en(ahora))
            .param("expiresAt", en(expiraEn))
            .update()
        == 1;
  }

  @Override
  public void completar(String clave, RespuestaAlmacenada respuestaHttp) {
    jdbc.sql(
            """
            update idempotency_keys
               set response_code = :code,
                   response_headers = cast(:headers as jsonb),
                   response_body = :body
             where key = :key
            """)
        .param("code", respuestaHttp.estado())
        .param("headers", json.writeValueAsString(respuestaHttp.cabeceras()))
        .param("body", respuestaHttp.cuerpo())
        .param("key", clave)
        .update();
  }

  private static OffsetDateTime en(Instant instante) {
    return instante.atOffset(ZoneOffset.UTC);
  }
}
