-- Soporta el listado de pedidos por rango de fechas, del más reciente al más antiguo (spec 013, ADR 0021).
create index orders_created_at_id on orders (created_at desc, id desc);
