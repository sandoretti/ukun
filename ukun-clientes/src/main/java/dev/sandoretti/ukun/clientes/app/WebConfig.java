package dev.sandoretti.ukun.clientes.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer
{
    @Autowired
    @Qualifier("clienteInterceptor")
    private HandlerInterceptor clienteInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // implementamos la ruta a los resources de la aplicacion
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("file:/opt/resources/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(clienteInterceptor)
                .addPathPatterns("/perfil/**", "/carrito/**", "/pedidos/**");
    }
}
