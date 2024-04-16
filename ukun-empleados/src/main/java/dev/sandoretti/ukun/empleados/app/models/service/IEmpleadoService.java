package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;

import java.util.List;

public interface IEmpleadoService
{
    public List<Empleado> findByTienda(Tienda tienda);

    /**
     * Encuentra un empleado dado su id
     * @param id Identificador del empleado
     * @return Empleado correspondiente
     */
    public Empleado findById(Long id);

    /**
     * Guarda o modifica el empleado
     * @param empleado Empleado a insertar o modificar
     */
    public void save(Empleado empleado);

    /**
     * Elimina el empleado dado su id
     * @param id Identificador del empleado
     */
    public void delete(Long id);
}
