package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.IClienteDAO;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteServiceImpl implements IClienteService
{
    @Autowired
    private IClienteDAO clienteDAO;

    @Override
    @Transactional(readOnly = true)
    public Cliente findById(Long id)
    {
        return clienteDAO.findById(id).orElse(null);
    }

    @Override
    public Cliente save(Cliente cliente)
    {
        return clienteDAO.save(cliente);
    }
}
