package br.edu.ifc.bikes.config;

import jakarta.annotation.PostConstruct;

import java.util.TimeZone;

public class SpringTimezoneConfig {

    @PostConstruct // garante que o metodo abaixo seja executado logo apos o construtor
    public void timezoneConfig(){
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}
