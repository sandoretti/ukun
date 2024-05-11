package dev.sandoretti.ukun.clientes.app.models.dao;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Pedido;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface IPedidoDAO extends CrudRepository<Pedido, Long>
{
    public List<Pedido> findByCliente(Cliente cliente);
}
