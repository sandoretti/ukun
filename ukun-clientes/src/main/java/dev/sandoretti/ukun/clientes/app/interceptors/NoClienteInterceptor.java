package dev.sandoretti.ukun.clientes.app.interceptors;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component("noClienteInterceptor")
public class NoClienteInterceptor implements HandlerInterceptor
{
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        Cliente cliente = (Cliente) request.getSession().getAttribute("cliente");

        // Si es un empleado que existe en la base de datos redirigimos a la ventana principal
        if (cliente != null && cliente.getId() != null && cliente.getId() > 0)
        {
            response.sendRedirect(request.getContextPath().concat("/"));
            return false;
        }

        // Sino lo damos por valido
        return true;
    }
}
