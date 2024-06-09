package dev.sandoretti.ukun.empleados.app.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IProductoCrud;
import dev.sandoretti.ukun.empleados.app.models.dao.ITipoProductoCrud;
import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.entity.TipoProducto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoServiceImpl implements IProductoService
{
    @Autowired
    private IProductoCrud productoCrud;

    @Autowired
    private ITipoProductoCrud tipoProductoCrud;

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

    @Override
    public void save(Producto producto) {
        productoCrud.save(producto);
    }

    @Override
    @Transactional
    public void deleteById(Long id)
    {
        productoCrud.deleteById(id);
    }

    @Override
    public List<TipoProducto> findAllTipoProductos() {
        return (List<TipoProducto>) tipoProductoCrud.findAll();
    }
}
