package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IProductoCrud;
import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoServiceImpl implements IProductoService
{
    @Autowired
    private IProductoCrud productoCrud;

    @Override
    @Transactional(readOnly = true)
    public Producto findById(Long id) {
        return productoCrud.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> findAll() {
        return (List<Producto>) productoCrud.findAll();
    }
}
