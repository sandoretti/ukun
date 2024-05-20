package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.IClienteDAO;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SignUpServiceImpl implements ISignUpService
{
    @Autowired
    private IClienteDAO clienteDAO;

    @Override
    public void registrar(Cliente cliente)
    {
        // Ponemos el correo en minusculas
        cliente.getCuenta().setCorreo(cliente.getCuenta().getCorreo().toLowerCase());
        clienteDAO.save(cliente);
    }

}
