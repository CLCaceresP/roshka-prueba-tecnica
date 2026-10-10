package com.roshka.challenge.pedidos.configuracion;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.adaptadores.salida.mensajeria.OutboxProperties;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "${outbox.publicador.bloqueo-maximo}")
@EnableConfigurationProperties({MensajeriaProperties.class, OutboxProperties.class})
class MensajeriaConfiguration {

  /**
   * Tópicos y sus tópicos de mensajes muertos; la DLT necesita tantas particiones como su origen.
   */
  @Bean
  KafkaAdmin.NewTopics topicosSaga(MensajeriaProperties properties) {
    var topicos = properties.topicos();
    return new KafkaAdmin.NewTopics(
        topico(Topicos.INVENTARIO_COMANDOS, topicos),
        topico(Topicos.INVENTARIO_COMANDOS + Topicos.SUFIJO_MENSAJE_MUERTO, topicos),
        topico(Topicos.INVENTARIO_EVENTOS, topicos),
        topico(Topicos.INVENTARIO_EVENTOS + Topicos.SUFIJO_MENSAJE_MUERTO, topicos));
  }

  @Bean
  LockProvider proveedorBloqueo(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }

  private static NewTopic topico(String nombre, MensajeriaProperties.Topicos topicos) {
    return TopicBuilder.name(nombre)
        .partitions(topicos.particiones())
        .replicas(topicos.replicas())
        .build();
  }
}
