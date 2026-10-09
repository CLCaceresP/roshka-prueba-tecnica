# 0008 · Plataforma: Java 25, Spring Boot 4.1 en los servicios y 4.0 en el gateway

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-01, REQ-28

## Contexto y problema

Hay que fijar las versiones de la plataforma. Todas se verificaron el 2026-10-09 contra Maven Central, Docker Hub, Spring Initializr y la API de GitHub; ninguna se escribió de memoria. La restricción relevante: Spring Cloud Gateway llega en el *release train* de Spring Cloud, que va un paso detrás de Spring Boot.

| Componente | Última estable verificada |
|---|---|
| Spring Boot | 4.1.1 (4.2.0 en milestone) |
| Spring Cloud | 2025.1.3, línea de Boot 4.0.x (2026.0.0 para Boot 4.1 solo en M1) |
| Spring Cloud Gateway | 5.0.3 (en 2025.1.3) |
| Java | 25 LTS, ofrecido por Initializr para Boot 4.1 |
| Testcontainers | 2.0.5, gestionado por el BOM de Boot 4.1.1 |
| Redisson | 4.8.0, compilado contra Spring Data 4.1 |
| ShedLock | 7.10.1, compilado contra Spring 7.0.9 / Boot 4.1.1 |
| ArchUnit | 1.5.1 |
| Spotless Maven Plugin | 3.10.4 |
| Maven Wrapper / Maven | 3.3.4 / 3.10.0 (`only-script`, como lo genera Initializr) |

## Factores de decisión

- Usar la última versión estable de cada pieza ("no inventar versiones").
- Usar solo combinaciones soportadas oficialmente.
- No depender de milestones.

## Opciones consideradas

1. **Todo en Boot 4.0.8 + Spring Cloud 2025.1.3.** Una sola versión de plataforma, pero los servicios renuncian a la última estable sin necesidad: no usan Spring Cloud.
2. **Todo en Boot 4.1.1 + Spring Cloud 2025.1.3.** Combinación no soportada oficialmente. El verificador de compatibilidad viene desactivado por defecto, pero sería apostar a que funciona.
3. **Todo en Boot 4.1.1 + Spring Cloud 2026.0.0-M1.** Depende de un milestone.
4. **Servicios en Boot 4.1.1; gateway en Boot 4.0.8 + Spring Cloud 2025.1.3.**

## Decisión

**Opción 4.**

- `ms-pedidos`, `ms-inventario` y `contrato-eventos` heredan del POM raíz, cuyo padre es `spring-boot-starter-parent:4.1.1`.
- `gateway` es un módulo del reactor, pero su padre es `spring-boot-starter-parent:4.0.8` e importa `spring-cloud-dependencies:2025.1.3`. No hereda del POM raíz; repite la configuración de Spotless.
- Java 25 en todos los módulos.
- Imágenes: `eclipse-temurin:25-jre`, `postgres:18-alpine`, `apache/kafka:4.2.1` (igual que el cliente `kafka-clients` 4.2.1 del BOM), `redis:8.8-alpine`, `jaegertracing/jaeger:2.22.0`, `otel/opentelemetry-collector-contrib:0.162.0`, `prom/prometheus:v3.15.0`, `grafana/grafana:13.2.3`, `grafana/k6:2.3.0`.
- Redisson se usa como cliente (`org.redisson:redisson`), no como starter: solo se necesita `RLock`, y el starter arrastra Spring Data Redis.

## Consecuencias

- Positivas: cada pieza está en su última versión estable y en una combinación soportada.
- Negativas: dos versiones de Boot en el repositorio. El gateway no comparte la gestión de dependencias del POM raíz.
- Riesgos: cuando salga Spring Cloud 2026.0 GA, el gateway debe subir a Boot 4.1. Es un cambio de una línea, que se registra como mejora.

## Cómo se verifica

`./mvnw verify` compila y prueba los cuatro módulos. El CI usa el mismo comando.

## Referencias

- https://spring.io/projects/spring-cloud (tabla de compatibilidad de *release trains*)
- https://start.spring.io (metadata v2.2)
