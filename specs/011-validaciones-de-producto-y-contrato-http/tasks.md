# 011 · Tareas

- [x] T-01 · El nombre del producto se normaliza y rechaza caracteres de control · cubre 011-AC-03, 011-AC-04 · nivel: unit · módulo: ms-inventario
- [x] T-02 · Un nombre repetido (sin distinguir mayúsculas ni espacios) responde 409 `PRODUCT_ALREADY_EXISTS` · cubre 011-AC-01, 011-AC-02, 011-AC-06 · nivel: unit + it · módulo: ms-inventario
- [x] T-03 · Índice único en la base (V4) y carrera entre solicitudes · cubre 011-AC-05, 011-AC-09 · nivel: it + concurrencia · módulo: ms-inventario
- [x] T-04 · La API HTTP rechaza propiedades desconocidas; Kafka sigue tolerante · cubre 011-AC-07, 011-AC-08 · nivel: it · módulo: ambos
- [x] T-05 · Tests de integración con nombres únicos; limpieza de los repetidos en desarrollo; README y colección Postman · cubre RN-06 · nivel: compose · módulo: infra
