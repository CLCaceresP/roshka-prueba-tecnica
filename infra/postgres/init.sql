-- Una base de datos por servicio, cada una de su propio usuario. PUBLIC pierde CONNECT, así que ningún usuario
-- puede abrir la base del otro servicio (REQ-16). Las credenciales son solo para desarrollo local.
CREATE USER orders_user WITH PASSWORD 'orders_user';
CREATE USER inventory_user WITH PASSWORD 'inventory_user';

CREATE DATABASE orders_db OWNER orders_user;
CREATE DATABASE inventory_db OWNER inventory_user;

REVOKE ALL ON DATABASE orders_db FROM PUBLIC;
REVOKE ALL ON DATABASE inventory_db FROM PUBLIC;
