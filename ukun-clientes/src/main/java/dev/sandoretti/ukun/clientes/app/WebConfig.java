package dev.sandoretti.ukun.clientes.app;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer
{
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // implementamos la ruta a los resources de la aplicacion
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("file:/opt/resources/");
    }
}
