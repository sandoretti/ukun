package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.embeddable.CarritoId;
import dev.sandoretti.ukun.clientes.app.models.entity.Carrito;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;

import java.util.List;

public interface ICarritoService
{
    public List<Carrito> findByCliente(Cliente cliente);

    public Carrito guardar(Carrito carrito);

    public void eliminar(CarritoId id);

    public void eliminar(List<Carrito> productosCarrito);
}
