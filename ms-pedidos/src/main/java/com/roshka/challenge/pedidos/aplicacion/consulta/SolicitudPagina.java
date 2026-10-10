package com.roshka.challenge.pedidos.aplicacion.consulta;

import com.roshka.challenge.pedidos.dominio.ParametroInvalido;

/** Página pedida por el cliente, ya validada contra los límites configurados. */
public record SolicitudPagina(int pagina, int tamanio) {

  public static SolicitudPagina de(
      Integer pagina, Integer tamanio, ConfiguracionPaginacion configuracion) {
    var paginaEfectiva = pagina == null ? 0 : pagina;
    var tamanioEfectivo = tamanio == null ? configuracion.tamanioPorDefecto() : tamanio;
    if (paginaEfectiva < 0) {
      throw new ParametroInvalido("El parámetro page debe ser 0 o mayor");
    }
    if (tamanioEfectivo < 1 || tamanioEfectivo > configuracion.tamanioMaximo()) {
      throw new ParametroInvalido(
          "El parámetro size debe estar entre 1 y " + configuracion.tamanioMaximo());
    }
    return new SolicitudPagina(paginaEfectiva, tamanioEfectivo);
  }

  public long desplazamiento() {
    return (long) pagina * tamanio;
  }
}
