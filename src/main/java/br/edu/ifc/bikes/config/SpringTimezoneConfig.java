package br.edu.ifc.bikes.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Fixa o fuso horario da API no horario oficial de Brasilia.
 * Sem o @Configuration a classe nao era registrada e o @PostConstruct nunca executava.
 */
@Configuration
public class SpringTimezoneConfig {

    @PostConstruct
    public void timezoneConfig() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}
