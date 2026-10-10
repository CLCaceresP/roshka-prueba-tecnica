package com.roshka.challenge.inventario.adaptadores.salida.bloqueo;

import org.redisson.api.RedissonClient;
import org.redisson.api.redisnode.RedisNodes;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/** Forma parte del grupo readiness: sin Redis, POST /products responde 503 (ADR 0005). */
@Component("redis")
class IndicadorSaludRedis extends AbstractHealthIndicator {

  private final RedissonClient redisson;

  IndicadorSaludRedis(RedissonClient redisson) {
    this.redisson = redisson;
  }

  @Override
  protected void doHealthCheck(Health.Builder constructor) {
    if (redisson.getRedisNodes(RedisNodes.SINGLE).pingAll()) {
      constructor.up();
    } else {
      constructor.down();
    }
  }
}
