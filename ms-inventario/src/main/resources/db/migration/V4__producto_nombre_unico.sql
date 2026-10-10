-- El nombre de un producto es único sin distinguir mayúsculas ni espacios de los extremos (spec 011, ADR 0020).
-- Falla si ya hay nombres repetidos: se limpian antes de aplicar esta migración.
create unique index products_name_unique on products (lower(btrim(name)));
