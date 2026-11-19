package br.edu.ifc.bikes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serve a interface didatica (HTML/CSS/JS puro) que fica em src/main/resources/static/app.
 * <p>
 * O projeto usa @EnableWebMvc, o que desliga o mapeamento automatico de recursos estaticos
 * do Spring Boot. Por isso o mapeamento e declarado aqui explicitamente.
 * A pagina inicial ("/") e entregue por PaginaInicialController.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
                .addResourceLocations("classpath:/static/app/");
        registry.addResourceHandler("/mobile/**")
                .addResourceLocations("classpath:/static/mobile/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/mobile", "/mobile/index.html");
        registry.addRedirectViewController("/mobile/", "/mobile/index.html");
    }
}
