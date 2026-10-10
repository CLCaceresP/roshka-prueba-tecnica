package com.roshka.challenge.inventario.adaptadores.entrada.mensajeria;

import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parsea los sobres y los contrasta con el contrato antes de que corra cualquier código de negocio.
 */
@Component
class LectorMensajes {

  /**
   * Campos de ruteo y de log, leídos antes de enlazar la carga con su tipo.
   *
   * @param resumen contenido legible de la carga (líneas, motivo, desenlace) para los logs
   */
  record VistaPrevia(
      String tipo,
      String idMensaje,
      String idPedido,
      String idCorrelacion,
      String idCausa,
      String resumen) {}

  private final JsonMapper json;

  /** Tolera campos nuevos en los mensajes: los cambios de contrato en Kafka son aditivos. */
  LectorMensajes(JsonMapper json) {
    this.json = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  VistaPrevia vistaPrevia(String valor) {
    try {
      var raiz = json.readTree(valor);
      var version = raiz.path("versionEsquema").asInt(-1);
      if (version != TiposMensaje.VERSION_ESQUEMA) {
        throw new MensajeMalformado("versionEsquema no soportada: " + version, null);
      }
      return new VistaPrevia(
          raiz.path("tipo").asString(""),
          raiz.path("idMensaje").asString(""),
          raiz.path("idAgregado").asString(""),
          raiz.path("idCorrelacion").asString(""),
          raiz.path("idCausa").asString(""),
          resumen(raiz.path("carga")));
    } catch (JacksonException e) {
      throw new MensajeMalformado("El registro no es un sobre JSON", e);
    }
  }

  <P> SobreMensaje<P> leer(String valor, Class<P> tipoCarga) {
    try {
      return json.readValue(
          valor, json.getTypeFactory().constructParametricType(SobreMensaje.class, tipoCarga));
    } catch (JacksonException | NullPointerException e) {
      throw new MensajeMalformado("El sobre no corresponde a " + tipoCarga.getSimpleName(), e);
    }
  }

  private static String resumen(JsonNode carga) {
    if (!carga.isObject()) {
      return "";
    }
    var partes = new ArrayList<String>();
    for (var nombre : carga.propertyNames()) {
      if (!nombre.equals("idPedido")) {
        partes.add(nombre + "=" + valor(carga.get(nombre)));
      }
    }
    return String.join("; ", partes);
  }

  private static String valor(JsonNode nodo) {
    if (!nodo.isArray()) {
      return nodo.asString("");
    }
    var elementos = new ArrayList<String>();
    for (var elemento : nodo) {
      elementos.add(
          elemento.isObject()
              ? elemento.path("idProducto").asString("")
                  + "×"
                  + elemento.path("cantidad").asString("")
              : elemento.asString(""));
    }
    return "[" + String.join(", ", elementos) + "]";
  }
}
