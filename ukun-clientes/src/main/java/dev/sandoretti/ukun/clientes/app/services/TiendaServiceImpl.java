package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.ITiendaDAO;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TiendaServiceImpl implements ITiendaService
{
    @Autowired
    private ITiendaDAO tiendaDAO;

    @Override
    @Transactional(readOnly = true)
    public List<Tienda> findAll() {
        return (List<Tienda>) tiendaDAO.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Tienda findById(Long id) {
        return tiendaDAO.findById(id).orElse(null);
    }
}
