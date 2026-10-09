package com.roshka.challenge.inventario;

import com.roshka.challenge.inventario.aplicacion.puertos.salida.MensajesProcesados;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.OutboxMensajes;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * Hace fallar llamadas elegidas a los puertos de salida, para ejercitar reintentos, rollbacks y
 * mensajes muertos.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InyeccionFallos {

  public static final class Fallos {

    private final Map<UUID, AtomicInteger> marcarProcesado = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> agregarRespuesta = new ConcurrentHashMap<>();

    /**
     * Los próximos {@code veces} intentos de registrar este mensaje como procesado fallan de forma
     * transitoria.
     */
    public void fallarAlMarcarProcesado(UUID idMensaje, int veces) {
      marcarProcesado.put(idMensaje, new AtomicInteger(veces));
    }

    /**
     * Las próximas {@code veces} respuestas de este pedido fallan después de aplicado el efecto de
     * negocio.
     */
    public void fallarAlAgregarRespuestaPara(UUID idPedido, int veces) {
      agregarRespuesta.put(idPedido, new AtomicInteger(veces));
    }

    private static void quizaFallar(Map<UUID, AtomicInteger> fallos, UUID id) {
      var restante = fallos.get(id);
      if (restante != null && restante.getAndDecrement() > 0) {
        throw new TransientDataAccessResourceException("injected transient failure for " + id);
      }
    }
  }

  @Bean
  Fallos fallos() {
    return new Fallos();
  }

  @Bean
  static BeanPostProcessor puertosDefectuosos(ObjectProvider<Fallos> fallos) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String nombreBean) {
        if (bean instanceof MensajesProcesados real) {
          return (MensajesProcesados)
              idMensaje -> {
                Fallos.quizaFallar(fallos.getObject().marcarProcesado, idMensaje);
                return real.marcarProcesado(idMensaje);
              };
        }
        if (bean instanceof OutboxMensajes real) {
          return (OutboxMensajes)
              (topico, sobre) -> {
                Fallos.quizaFallar(fallos.getObject().agregarRespuesta, sobre.idAgregado());
                real.agregar(topico, sobre);
              };
        }
        return bean;
      }
    };
  }
}
