package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;

public interface ILoginService
{
    /**
     * Encuentra el empleado dado su correo y contrasenna
     * @param correo Correo asociada al empleado
     * @param contrasenna Contrasenna asociada al empleado
     * @return Empleado correspodiente
     */
    public Empleado loginEmpleado(String correo, String contrasenna);
}
