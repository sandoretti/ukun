package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IEmpleadoService
{
    /**
     * Busca todos los empleados
     * @return Lista de todos los empleados
     */
    public List<Empleado> findAll();

    /**
     * Lista todos los empleados por paginas
     * @param pageable Paginable
     * @return Lista de empleados en paginas
     */
    public Page<Empleado> findAll(Pageable pageable);

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
