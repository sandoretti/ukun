package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.IProductoDAO;
import dev.sandoretti.ukun.clientes.app.models.entity.Producto;
import dev.sandoretti.ukun.clientes.app.utilities.filtros.ProductoFiltro;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoServiceImpl implements IProductoService
{
    @Autowired
    private IProductoDAO productoDAO;

    @Override
    @Transactional(readOnly = true)
    public Producto findById(Long id) {
        return productoDAO.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> filtrarProductos(String nombre, Long idTipo, Integer ordenPrecio)
    {
        Specification<Producto> specification = Specification.where(null);

        if (nombre != null && !nombre.isEmpty())
        {
            specification = specification.and(ProductoFiltro.porNombre(nombre));
        }

        if (idTipo != null)
        {
            specification = specification.and(ProductoFiltro.porTipo(idTipo));
        }

        if (ordenPrecio == null)
            return productoDAO.findAll(specification);

        return switch (ordenPrecio) {
            case ProductoFiltro.ASC -> productoDAO.findAll(specification, Sort.by(Sort.Direction.ASC, "precio"));
            case ProductoFiltro.DESC -> productoDAO.findAll(specification, Sort.by(Sort.Direction.DESC, "precio"));
            default -> productoDAO.findAll(specification);
        };
    }
}
