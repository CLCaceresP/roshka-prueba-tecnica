package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Traduce la ruta de un campo del DTO (por ejemplo {@code lineas[0].cantidad}) al nombre que ve el
 * cliente en el JSON ({@code lines[0].quantity}).
 */
final class NombresJson {

  private NombresJson() {}

  static String de(Class<?> tipo, String ruta) {
    var traducidos = new ArrayList<String>();
    Class<?> actual = tipo;
    for (var segmento : ruta.split("\\.")) {
      var corchete = segmento.indexOf('[');
      var nombre = corchete < 0 ? segmento : segmento.substring(0, corchete);
      var indice = corchete < 0 ? "" : segmento.substring(corchete);
      var componente = actual == null ? null : componente(actual, nombre);
      if (componente == null) {
        traducidos.add(segmento);
        actual = null;
        continue;
      }
      var json = componente.getAccessor().getAnnotation(JsonProperty.class);
      traducidos.add((json == null ? nombre : json.value()) + indice);
      actual = tipoDeElemento(componente);
    }
    return String.join(".", traducidos);
  }

  private static RecordComponent componente(Class<?> tipo, String nombre) {
    if (!tipo.isRecord()) {
      return null;
    }
    for (var componente : tipo.getRecordComponents()) {
      if (componente.getName().equals(nombre)) {
        return componente;
      }
    }
    return null;
  }

  private static Class<?> tipoDeElemento(RecordComponent componente) {
    if (Collection.class.isAssignableFrom(componente.getType())
        && componente.getGenericType() instanceof ParameterizedType parametrizado
        && parametrizado.getActualTypeArguments()[0] instanceof Class<?> elemento) {
      return elemento;
    }
    return componente.getType();
  }
}
