package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;

public interface ILoginService
{
    /**
     * Obtener el cliente dado su correo y contrasenna. Si no se encuentra, devuelve el cliente como nulo
     * @param correo Correo del cliente
     * @param contrasenna Contrasenna del cliente
     * @return Cliente asociado al correo y contrasenna
     */
    public Cliente login(String correo, String contrasenna);
}
