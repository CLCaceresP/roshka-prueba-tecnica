-- El total de un producto no puede superar 10.000.000 (spec 014, ADR 0022).
alter table products add constraint products_total_maximo check (total <= 10000000);
