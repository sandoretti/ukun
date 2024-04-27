package dev.sandoretti.ukun.empleados.app.interceptors;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component("empleadoInterceptor")
public class EmpleadoInterceptor implements HandlerInterceptor
{
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        // Obtenemos el empleado de la sesion
        Empleado empleado = (Empleado) request.getSession().getAttribute("empleado");

        // Si es un empleado que existe en la base de datos
        if (empleado != null && empleado.getId() != null && empleado.getId() > 0)
            return true;

        // Sino redirigimos a login
        response.sendRedirect(request.getContextPath().concat("/login"));
        return false;

    }
}
