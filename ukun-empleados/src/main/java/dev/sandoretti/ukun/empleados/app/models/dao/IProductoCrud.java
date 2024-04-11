package dev.sandoretti.ukun.empleados.app.models.dao;

import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import org.springframework.data.repository.CrudRepository;

public interface IProductoCrud extends CrudRepository<Producto, Long>
{

}
