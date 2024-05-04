package dev.sandoretti.ukun.clientes.app.models.dao;

import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import org.springframework.data.repository.CrudRepository;

public interface ITiendaDAO extends CrudRepository<Tienda, Long>
{

}
