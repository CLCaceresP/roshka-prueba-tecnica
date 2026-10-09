package com.roshka.challenge.contrato;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sobre compartido por todo comando y evento que circula por Kafka.
 *
 * <p>{@code idMensaje} es la clave de deduplicación de los consumidores, {@code idAgregado} (el id
 * del pedido) es la key del record de Kafka, {@code idCausa} es el {@code idMensaje} del mensaje
 * que causó este ({@code null} si lo causó una solicitud HTTP o un job programado) y {@code
 * idCorrelacion} se propaga sin cambios a lo largo de toda la saga.
 */
public record SobreMensaje<P>(
    UUID idMensaje,
    String tipo,
    int versionEsquema,
    Instant ocurridoEn,
    String idCorrelacion,
    UUID idCausa,
    UUID idAgregado,
    P carga) {

  public SobreMensaje {
    Objects.requireNonNull(idMensaje, "idMensaje");
    Objects.requireNonNull(tipo, "tipo");
    Objects.requireNonNull(ocurridoEn, "ocurridoEn");
    Objects.requireNonNull(idCorrelacion, "idCorrelacion");
    Objects.requireNonNull(idAgregado, "idAgregado");
    Objects.requireNonNull(carga, "carga");
  }
}
