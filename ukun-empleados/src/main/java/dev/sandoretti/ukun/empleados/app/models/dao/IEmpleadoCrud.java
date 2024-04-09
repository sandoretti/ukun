package dev.sandoretti.ukun.empleados.app.models.dao;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface IEmpleadoCrud extends PagingAndSortingRepository<Empleado, Long>, CrudRepository<Empleado, Long>
{
    @Query("SELECT e FROM Empleado e WHERE e.cuenta.correo = ?1 AND e.cuenta.contrasenna = ?2")
    public Empleado findByCorreoAndContrasenna(String correo, String contrasenna);
}
