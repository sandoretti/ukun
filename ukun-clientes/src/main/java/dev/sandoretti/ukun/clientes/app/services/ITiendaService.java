package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;

import java.util.List;

public interface ITiendaService
{
    public List<Tienda> findAll();
    public Tienda findById(Long id);
    public Tienda findByNombre(String nombre);
}
