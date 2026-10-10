package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * SHA-256 del método, la ruta y el cuerpo ya parseado. Serializar el record de la solicitud da una
 * forma canónica: los espacios y el orden de las claves del JSON original no cambian la huella.
 */
@Component
class HuellaSolicitud {

  private final JsonMapper json;

  HuellaSolicitud(JsonMapper json) {
    this.json = json;
  }

  String de(String metodo, String ruta, Object cuerpo) {
    var canonico =
        metodo + " " + ruta + "\n" + (cuerpo == null ? "" : json.writeValueAsString(cuerpo));
    try {
      var resumen = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(resumen.digest(canonico.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 es obligatorio en toda plataforma Java", e);
    }
  }
}
