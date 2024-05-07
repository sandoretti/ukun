package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.ICarritoDAO;
import dev.sandoretti.ukun.clientes.app.models.embeddable.CarritoId;
import dev.sandoretti.ukun.clientes.app.models.entity.Carrito;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CarritoServiceImpl implements ICarritoService
{
    @Autowired
    private ICarritoDAO carritoDAO;

    @Override
    public List<Carrito> findByCliente(Cliente cliente)
    {
        return carritoDAO.findByCliente(cliente);
    }

    @Override
    public Carrito guardar(Carrito carrito)
    {
        return carritoDAO.save(carrito);
    }

    @Override
    public void eliminar(CarritoId id)
    {
        carritoDAO.deleteById(id);
    }
}
