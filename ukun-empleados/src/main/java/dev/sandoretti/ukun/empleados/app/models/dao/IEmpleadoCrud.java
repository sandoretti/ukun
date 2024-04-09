package dev.sandoretti.ukun.empleados.app.models.dao;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface IEmpleadoCrud extends PagingAndSortingRepository<Empleado, Long>, CrudRepository<Empleado, Long> {}
