package com.roshka.challenge.inventario.adaptadores.entrada.rest;

/** Textos y ejemplos del documento OpenAPI; las anotaciones solo aceptan constantes. */
final class Documentacion {

  static final String TIPO_PROBLEMA = "application/problem+json";

  static final String CLAVE_IDEMPOTENCIA =
      "Clave única de la operación. Si se repite con el mismo cuerpo se devuelve la respuesta "
          + "original (header Idempotent-Replayed: true); con otro cuerpo, 422.";
  static final String FORMATO_CLAVE = "[A-Za-z0-9_.:-]{1,100}";
  static final String EJEMPLO_CLAVE = "a3f1c2d4-5b6e-4f70-8a9b-0c1d2e3f4a5b";

  static final String PAGINA = "Número de página, empieza en 0.";
  static final String TAMANIO = "Elementos por página, de 1 a 100.";

  static final String REINTENTAR_DESPUES = "Segundos a esperar antes de reintentar.";

  private static final String PRODUCTO = "6805e54f-71b3-4e67-b3c0-b40ea638fac1";

  static final String VALIDACION =
      """
      {"type":"about:blank","title":"Bad Request","status":400,\
      "detail":"El parámetro size debe estar entre 1 y 100","instance":"/products",\
      "code":"VALIDATION_FAILED"}""";
  static final String CLAVE_AUSENTE =
      """
      {"type":"about:blank","title":"Bad Request","status":400,\
      "detail":"Las solicitudes de escritura requieren el header Idempotency-Key",\
      "instance":"/products","code":"IDEMPOTENCY_KEY_MISSING"}""";
  static final String CLAVE_INVALIDA =
      """
      {"type":"about:blank","title":"Bad Request","status":400,\
      "detail":"Idempotency-Key debe tener de 1 a 100 caracteres entre A-Z a-z 0-9 _ . : -",\
      "instance":"/products","code":"IDEMPOTENCY_KEY_INVALID"}""";
  static final String PRODUCTO_NO_ENCONTRADO =
      """
      {"type":"about:blank","title":"Not Found","status":404,\
      "detail":"El producto 6805e54f-71b3-4e67-b3c0-b40ea638fac1 no existe",\
      "instance":"/products/6805e54f-71b3-4e67-b3c0-b40ea638fac1","code":"PRODUCT_NOT_FOUND"}""";
  static final String PRODUCTO_REPETIDO =
      """
      {"type":"about:blank","title":"Conflict","status":409,\
      "detail":"Ya existe el producto 6805e54f-71b3-4e67-b3c0-b40ea638fac1 con el nombre «Teclado»",\
      "instance":"/products","code":"PRODUCT_ALREADY_EXISTS"}""";
  static final String CLAVE_EN_PROCESO =
      """
      {"type":"about:blank","title":"Conflict","status":409,\
      "detail":"Una solicitud con la misma Idempotency-Key está en proceso",\
      "instance":"/products","code":"IDEMPOTENCY_KEY_IN_PROGRESS"}""";
  static final String LIMITE_DE_STOCK =
      """
      {"type":"about:blank","title":"Conflict","status":409,\
      "detail":"El producto 6805e54f-71b3-4e67-b3c0-b40ea638fac1 tiene 5 unidades reservadas; dejar 10000000 disponibles superaría el máximo de 10000000",\
      "instance":"/products/6805e54f-71b3-4e67-b3c0-b40ea638fac1/stock",\
      "code":"STOCK_LIMIT_EXCEEDED"}""";
  static final String CLAVE_REUTILIZADA =
      """
      {"type":"about:blank","title":"Unprocessable Content","status":422,\
      "detail":"La Idempotency-Key ya se usó en una solicitud distinta","instance":"/products",\
      "code":"IDEMPOTENCY_KEY_REUSED"}""";
  static final String BLOQUEO_NO_DISPONIBLE =
      """
      {"type":"about:blank","title":"Service Unavailable","status":503,\
      "detail":"Servicio de bloqueo no disponible al adquirir idem:ms-inventario:a3f1c2d4",\
      "instance":"/products","code":"LOCK_UNAVAILABLE"}""";

  private Documentacion() {}
}
