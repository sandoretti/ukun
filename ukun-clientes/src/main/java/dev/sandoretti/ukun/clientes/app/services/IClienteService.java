package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;

public interface IClienteService
{
    /**
     * Encuentra el cliente por su identificador
     * @param id Identificador del cliente
     * @return Cliente si existe, sino nulo
     */
    public Cliente findById(Long id);

    /**
     * Guarda el cliente en la base de datos
     * @param cliente Cliente a guardar
     */
    public void save(Cliente cliente);
}
