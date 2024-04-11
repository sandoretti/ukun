package dev.sandoretti.ukun.empleados.app.interceptors;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component("adminInterceptor")
public class AdminInterceptor implements HandlerInterceptor
{
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        // Obtenemos el empleado de la sesion
        Empleado empleado = (Empleado) request.getSession().getAttribute("empleado");

        // Si es un empleado que existe en la base de datos
        if (empleado != null && empleado.getIsAdmin())
        {
            return true;
        }

        // Sino redirigimos a la pagina principal
        response.sendRedirect(request.getContextPath());
        return false;
    }


}
