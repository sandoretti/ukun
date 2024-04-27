package dev.sandoretti.ukun.empleados.app.models.dao;

import dev.sandoretti.ukun.empleados.app.models.entity.TipoProducto;
import org.springframework.data.repository.CrudRepository;

public interface ITipoProductoCrud extends CrudRepository<TipoProducto, Long>
{

}
