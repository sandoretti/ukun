package dev.sandoretti.ukun.clientes.app.models.dao;

import dev.sandoretti.ukun.clientes.app.models.embeddable.CarritoId;
import dev.sandoretti.ukun.clientes.app.models.entity.Carrito;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ICarritoDAO extends CrudRepository<Carrito, CarritoId>
{
    public List<Carrito> findByCliente(Cliente cliente);
}
