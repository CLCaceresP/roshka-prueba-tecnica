package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

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
  static final String DESDE =
      "Fecha inicial en formato MM/dd/yyyy. Si no se envía ninguna fecha se usa hoy. "
          + "El rango no puede superar los 31 días.";
  static final String HASTA =
      "Fecha final inclusive en formato MM/dd/yyyy. Si solo se envía una de las dos, el rango es "
          + "ese día. Máximo 31 días.";
  static final String EJEMPLO_FECHA = "10/01/2026";

  static final String REINTENTAR_DESPUES = "Segundos a esperar antes de reintentar.";

  static final String VALIDACION =
      """
      {"type":"about:blank","title":"Bad Request","status":400,\
      "detail":"El parámetro from debe tener formato MM/dd/yyyy y ser una fecha válida (por ejemplo 10/01/2026)",\
      "instance":"/orders","code":"VALIDATION_FAILED"}""";
  static final String CLAVE_AUSENTE =
      """
      {"type":"about:blank","title":"Bad Request","status":400,\
      "detail":"Las solicitudes de escritura requieren el header Idempotency-Key",\
      "instance":"/orders","code":"IDEMPOTENCY_KEY_MISSING"}""";
  static final String CLAVE_INVALIDA =
      """
      {"type":"about:blank","title":"Bad Request","status":400,\
      "detail":"Idempotency-Key debe tener de 1 a 100 caracteres entre A-Z a-z 0-9 _ . : -",\
      "instance":"/orders","code":"IDEMPOTENCY_KEY_INVALID"}""";
  static final String LINEA_DUPLICADA =
      """
      {"type":"about:blank","title":"Bad Request","status":400,\
      "detail":"El producto 6805e54f-71b3-4e67-b3c0-b40ea638fac1 aparece en más de una línea",\
      "instance":"/orders","code":"DUPLICATE_PRODUCT_LINE"}""";
  static final String PEDIDO_NO_ENCONTRADO =
      """
      {"type":"about:blank","title":"Not Found","status":404,\
      "detail":"El pedido 12670fca-2289-4691-96fe-491cba878632 no existe",\
      "instance":"/orders/12670fca-2289-4691-96fe-491cba878632","code":"ORDER_NOT_FOUND"}""";
  static final String CLAVE_EN_PROCESO =
      """
      {"type":"about:blank","title":"Conflict","status":409,\
      "detail":"Una solicitud con la misma Idempotency-Key está en proceso",\
      "instance":"/orders","code":"IDEMPOTENCY_KEY_IN_PROGRESS"}""";
  static final String NO_CANCELABLE =
      """
      {"type":"about:blank","title":"Conflict","status":409,\
      "detail":"Un pedido en estado REJECTED no se puede cancelar",\
      "instance":"/orders/12670fca-2289-4691-96fe-491cba878632","code":"ORDER_NOT_CANCELLABLE"}""";
  static final String MODIFICACION_CONCURRENTE =
      """
      {"type":"about:blank","title":"Conflict","status":409,\
      "detail":"El pedido se está modificando concurrentemente",\
      "instance":"/orders/12670fca-2289-4691-96fe-491cba878632","code":"CONCURRENT_MODIFICATION"}""";
  static final String CLAVE_REUTILIZADA =
      """
      {"type":"about:blank","title":"Unprocessable Content","status":422,\
      "detail":"La Idempotency-Key ya se usó en una solicitud distinta",\
      "instance":"/orders","code":"IDEMPOTENCY_KEY_REUSED"}""";
  static final String BLOQUEO_NO_DISPONIBLE =
      """
      {"type":"about:blank","title":"Service Unavailable","status":503,\
      "detail":"Servicio de bloqueo no disponible al adquirir idem:ms-pedidos:a3f1c2d4",\
      "instance":"/orders","code":"LOCK_UNAVAILABLE"}""";

  private Documentacion() {}
}
