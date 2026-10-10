package com.roshka.challenge.pedidos.configuracion;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("consulta")
record ConsultaProperties(ZoneId zonaHoraria, int rangoMaximoDias) {}
