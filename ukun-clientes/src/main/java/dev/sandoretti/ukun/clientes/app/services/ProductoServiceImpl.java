package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.IProductoDAO;
import dev.sandoretti.ukun.clientes.app.models.dao.IStockProductoDAO;
import dev.sandoretti.ukun.clientes.app.models.dao.ITiendaDAO;
import dev.sandoretti.ukun.clientes.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.clientes.app.models.entity.Producto;
import dev.sandoretti.ukun.clientes.app.models.entity.StockProducto;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import dev.sandoretti.ukun.clientes.app.utilities.filtros.ProductoFiltro;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
public class ProductoServiceImpl implements IProductoService
{
    @Autowired
    private IProductoDAO productoDAO;

    @Autowired
    private IStockProductoDAO stockProductoDAO;

    private final Tienda tiendaOnline;

    @Autowired
    public ProductoServiceImpl(ITiendaDAO tiendaDAO)
    {
        tiendaOnline = tiendaDAO.findByNombre("Ukun Online");
    }

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

    @Override
    @Transactional(readOnly = true)
    public HashMap<Long, Boolean> disponibilidadTienda(Tienda tienda, List<Producto> productos)
    {
        // Obtenemos el stock de los productos en la tienda dada
        List<StockProducto> stockProductosOnline =
                stockProductoDAO.findStockProductoByTiendaAndProductoIn(tienda, productos);

        // Inicializamos el hashmap de los productos
        HashMap<Long, Boolean> disponibleProductoHash = new HashMap<>();

        // Inicializamos parametros necesarios para la asignacion
        Long idProducto;
        boolean disponibleProducto;

        // Recorremos todos los stocks
        for (StockProducto stock : stockProductosOnline)
        {
            // Obtenemos el id del producto del stock
            idProducto = stock.getId().getProductoId();

            // Miramos si el producto esta disponible si el stock del producto es mayor a 0
            disponibleProducto = stock.getStock() > 0;

            // Lo metemos en el hasmap
            disponibleProductoHash.put(idProducto, disponibleProducto);
        }

        // Devolvemos el Hashmap
        return disponibleProductoHash;
    }

    @Override
    @Transactional(readOnly = true)
    public HashMap<Long, Boolean> disponibilidadTiendaOnline(List<Producto> productos)
    {
        return disponibilidadTienda(tiendaOnline, productos);
    }

    @Override
    public Long obtenerStockProductoTienda(Tienda tienda, Producto producto) {
        // Creamos el id a buscar
        StockProductoId id = new StockProductoId(tienda.getId(), producto.getId());

        // Obtenemos el stock por su id
        StockProducto stock = stockProductoDAO.findById(id).orElse(null);

        // Devolvemos el stock, si no se ha encontrado, el stock que se devuelve es 0
        return (stock == null) ? 0 : stock.getStock();
    }

    @Override
    public Long obtenerStockProductoTiendaOnline(Producto producto) {
        return obtenerStockProductoTienda(tiendaOnline, producto);
    }
}
