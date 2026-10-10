package com.roshka.challenge.pedidos;

import com.roshka.challenge.pedidos.aplicacion.puertos.salida.OutboxMensajes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.TransientDataAccessResourceException;

/** Hace fallar llamadas elegidas a los puertos de salida, para ejercitar rollbacks. */
@TestConfiguration(proxyBeanMethods = false)
public class InyeccionFallos {

  public static final class Fallos {

    private final Map<String, AtomicInteger> agregarPorCorrelacion = new ConcurrentHashMap<>();

    /**
     * Las próximas {@code veces} escrituras del outbox con este id de correlación fallan después
     * del efecto.
     */
    public void fallarAlAgregarConCorrelacion(String idCorrelacion, int veces) {
      agregarPorCorrelacion.put(idCorrelacion, new AtomicInteger(veces));
    }

    private void quizaFallar(String idCorrelacion) {
      var restante = agregarPorCorrelacion.get(idCorrelacion);
      if (restante != null && restante.getAndDecrement() > 0) {
        throw new TransientDataAccessResourceException("injected failure for " + idCorrelacion);
      }
    }
  }

  @Bean
  Fallos fallos() {
    return new Fallos();
  }

  @Bean
  static BeanPostProcessor outboxDefectuoso(ObjectProvider<Fallos> fallos) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String nombreBean) {
        if (bean instanceof OutboxMensajes real) {
          return (OutboxMensajes)
              (topico, sobre) -> {
                fallos.getObject().quizaFallar(sobre.idCorrelacion());
                real.agregar(topico, sobre);
              };
        }
        return bean;
      }
    };
  }
}
