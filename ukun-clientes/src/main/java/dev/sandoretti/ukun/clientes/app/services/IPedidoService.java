package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Pedido;

import java.util.List;

public interface IPedidoService
{
    public List<Pedido> obtenerPorCliente(Cliente cliente);

    public Pedido obtenerPorId(Long id);

    public Pedido guardar(Cliente cliente);
}
