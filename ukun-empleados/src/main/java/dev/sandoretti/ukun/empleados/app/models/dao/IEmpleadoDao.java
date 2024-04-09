package dev.sandoretti.ukun.empleados.app.models.dao;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;

import java.util.List;

public interface IEmpleadoDao
{
    /**
     * Devuelve todos los empleados exitentes en la base de datos
     * @return Lista de empleados
     */
    public List<Empleado> findAll();

    /**
     * Busca un empleado por un id dado
     * @param id Identificador del empleado
     * @return Empleado
     */
    public Empleado findById(Long id);

    /**
     * Almacena el empleado o si existe, actualiza sus datos
     * @param empleado Empleado a insertar o actualizar
     */
    public void save(Empleado empleado);

    /**
     * Elimina el empleado dado su id
     * @param id Identificador del empleado
     */
    public void delete(Long id);

    /**
     * Dada una tienda, devuelve una lista de empleados pertenecientes a la tienda
     * @param tienda Tienda de los empleados
     * @return Lista de empleados pertenecientes a la tienda
     */
    public List<Empleado> findEmpleadosTienda(Tienda tienda);
}
