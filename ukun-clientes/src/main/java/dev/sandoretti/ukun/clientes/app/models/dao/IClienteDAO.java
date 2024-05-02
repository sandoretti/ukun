package dev.sandoretti.ukun.clientes.app.models.dao;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import org.springframework.data.repository.CrudRepository;

public interface IClienteDAO extends CrudRepository<Cliente, Long>
{
    public Cliente findByCuenta_CorreoAndCuenta_Contrasenna(String correo, String contrasena);
}
