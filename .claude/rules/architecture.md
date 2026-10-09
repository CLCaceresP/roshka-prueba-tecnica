---
paths:
  - "**/src/main/java/**"
  - "**/*ArchTest.java"
---

# Arquitectura hexagonal

## Capas y dependencias permitidas

```
adaptadores/entrada ──►  aplicacion (puertos/entrada) ──►  dominio
adaptadores/salida  ──►  aplicacion (puertos/salida)  ──►  dominio
configuracion       ──►  todo (solo wiring)
```

| Capa | Puede depender de | No puede depender de |
|---|---|---|
| `dominio` | JDK y otros paquetes de `dominio` | Spring, Jakarta/JPA, Kafka, Jackson, Redisson, `aplicacion`, `adaptadores`, `contrato-eventos` |
| `aplicacion` | `dominio`, sus propios puertos, `contrato-eventos` (lenguaje publicado de la saga) y, como únicas piezas de framework, `@Transactional`, los estereotipos (`@Service`, `@Component`) y la jerarquía de excepciones de `org.springframework.dao` (agnóstica de tecnología) | `adaptadores`, `configuracion`, tipos de infraestructura (JPA, Spring Data, Kafka, HTTP, Redisson, Jackson, Micrometer) |
| `adaptadores.entrada` | `aplicacion.puertos.entrada`, `contrato-eventos`, `dominio` (solo lectura) | `adaptadores.salida`, `dominio` mutado directamente |
| `adaptadores.salida` | `aplicacion.puertos.salida`, `dominio`, `contrato-eventos` | `adaptadores.entrada` |
| `configuracion` | todo | — |

Lo verifica un test de ArchUnit por servicio (`ArquitecturaHexagonalArchTest`). Si el test falla, se corrige el código; nunca se relaja la regla sin un ADR. Las reglas usan `allowEmptyShould(true)`: al cambiar el nombre de un paquete se comprueba que la regla siga evaluando clases y no pase en vacío.

## Reglas

- **Controllers y listeners son traductores:** parsean, validan el formato, llaman a un puerto de entrada y traducen el resultado o la excepción. No toman decisiones de negocio ni tocan repositorios.
- **Un caso de uso = una clase** que implementa un puerto de `entrada`. Define el límite transaccional.
- **El dominio decide:** las invariantes y la máquina de estados viven en las entidades y los value objects del dominio, no en los casos de uso.
- **Las entidades JPA no son el dominio.** `adaptadores/salida/persistencia` mapea entre entidades JPA y objetos de dominio.
- **Los adaptadores no se llaman entre sí.** Si un listener necesita publicar, lo hace vía un caso de uso que escribe en el outbox.
- **El orquestador de la saga** vive en `ms-pedidos`, en `aplicacion/saga`. Reacciona a eventos y decide el siguiente comando. No tiene acceso directo a Kafka.
- **El gateway** (`gateway-pedidos-inventario`) no contiene lógica de negocio: ni validación de body ni reglas de dominio.
- **Base de datos por servicio:** cada servicio solo tiene credenciales de su propia base.
- **Los enums persistidos o expuestos por HTTP conservan sus constantes en inglés** (`PENDING`, `REJECTED`, `INSUFFICIENT_STOCK`). Los enums del contrato de mensajes están en español, así que la aplicación convierte entre ambos con un `switch` exhaustivo; `valueOf(name())` solo se admite entre enums cuyas constantes se llaman igual (`CausaLiberacion` → `MotivoLiberacion`).
