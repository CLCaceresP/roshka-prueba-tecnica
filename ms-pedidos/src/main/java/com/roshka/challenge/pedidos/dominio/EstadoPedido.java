package com.roshka.challenge.pedidos.dominio;

import static com.roshka.challenge.pedidos.dominio.CausaLiberacion.CANCELADO_POR_CLIENTE;
import static com.roshka.challenge.pedidos.dominio.CausaLiberacion.COMPENSACION_RESERVA_TARDIA;
import static com.roshka.challenge.pedidos.dominio.CausaLiberacion.TIMEOUT_SAGA;

/**
 * Patrón State: cada estado decide cómo reacciona a cada entrada (ADR 0012). Leer una constante
 * equivale a leer una fila de la tabla del Pedido de la spec 004. Una entrada tardía nunca saca de
 * un estado terminal.
 */
public enum EstadoPedido {
  PENDING {
    @Override
    public Transicion alCancelar() {
      return Transicion.hasta(CANCELLED).liberando(CANCELADO_POR_CLIENTE);
    }

    @Override
    public Transicion alStockReservado() {
      return Transicion.hasta(CONFIRMED);
    }

    @Override
    public Transicion alStockRechazado() {
      return Transicion.hasta(REJECTED);
    }

    @Override
    public Transicion alTimeout() {
      return Transicion.hasta(REJECTED).liberando(TIMEOUT_SAGA);
    }
  },

  CONFIRMED {
    @Override
    public Transicion alCancelar() {
      return Transicion.hasta(CANCELLED).liberando(CANCELADO_POR_CLIENTE);
    }

    @Override
    public Transicion alStockReservado() {
      return Transicion.hasta(this);
    }

    /**
     * Inventario solo rechaza reservas que no existen o que fueron liberadas: es un bug del
     * contrato.
     */
    @Override
    public Transicion alStockRechazado() {
      throw new TransicionPedidoInvalida(this, "StockRechazado");
    }

    @Override
    public Transicion alTimeout() {
      return Transicion.hasta(this);
    }
  },

  REJECTED {
    @Override
    public Transicion alCancelar() {
      throw new PedidoNoCancelable(this);
    }

    @Override
    public Transicion alStockReservado() {
      return Transicion.hasta(this).liberando(COMPENSACION_RESERVA_TARDIA);
    }

    @Override
    public Transicion alStockRechazado() {
      return Transicion.hasta(this);
    }

    @Override
    public Transicion alTimeout() {
      return Transicion.hasta(this);
    }
  },

  CANCELLED {
    @Override
    public Transicion alCancelar() {
      return Transicion.hasta(this);
    }

    @Override
    public Transicion alStockReservado() {
      return Transicion.hasta(this).liberando(COMPENSACION_RESERVA_TARDIA);
    }

    @Override
    public Transicion alStockRechazado() {
      return Transicion.hasta(this);
    }

    @Override
    public Transicion alTimeout() {
      return Transicion.hasta(this);
    }
  };

  public abstract Transicion alCancelar();

  public abstract Transicion alStockReservado();

  public abstract Transicion alStockRechazado();

  public abstract Transicion alTimeout();
}
