package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.roshka.challenge.pedidos.aplicacion.idempotencia.RespuestaAlmacenada;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.RespuestaIdempotente;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Traduce entre HTTP y las respuestas registradas para las claves de idempotencia. */
@Component
class HttpIdempotente {

  static final String CABECERA_CLAVE = "Idempotency-Key";
  static final String CABECERA_REPRODUCIDO = "Idempotent-Replayed";

  private final JsonMapper json;

  HttpIdempotente(JsonMapper json) {
    this.json = json;
  }

  RespuestaAlmacenada registro(int estado, Map<String, String> cabeceras, Object cuerpo) {
    return new RespuestaAlmacenada(estado, cabeceras, json.writeValueAsString(cuerpo));
  }

  ResponseEntity<String> aHttp(RespuestaIdempotente resultado) {
    var respuestaHttp = resultado.respuestaHttp();
    var constructor =
        ResponseEntity.status(respuestaHttp.estado()).contentType(MediaType.APPLICATION_JSON);
    respuestaHttp.cabeceras().forEach(constructor::header);
    if (resultado.reproducido()) {
      constructor.header(CABECERA_REPRODUCIDO, "true");
    }
    return constructor.body(respuestaHttp.cuerpo());
  }
}
