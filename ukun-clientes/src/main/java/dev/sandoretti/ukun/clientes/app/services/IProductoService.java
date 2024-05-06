package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.entity.Producto;

import java.util.List;

public interface IProductoService
{
    public List<Producto> findAll();
    public Producto findById(Long id);
}
