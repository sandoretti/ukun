package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.IPedidoDAO;
import dev.sandoretti.ukun.clientes.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.clientes.app.models.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoServiceImpl implements IPedidoService
{
    @Autowired
    private ICarritoService carritoService;

    @Autowired
    private IStockService stockService;

    @Autowired
    private IPedidoDAO pedidoDAO;

    private final Long idTiendaOnline;

    @Autowired
    public PedidoServiceImpl(ITiendaService tiendaService)
    {
        this.idTiendaOnline = tiendaService.findByNombre("Ukun Online").getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> obtenerPorCliente(Cliente cliente)
    {
        return pedidoDAO.findByCliente(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public Pedido obtenerPorId(Long id)
    {
        return pedidoDAO.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public Pedido guardar(Cliente cliente)
    {
        // Obtenemos los productos del carrito del cliente
        List<Carrito> productosCarrito = carritoService.findByCliente(cliente);

        // Obtenemos el stock del online verificando si se puede realizar el pedido
        List<StockProducto> stockOnline = reducirStockOnline(productosCarrito);

        // Si al reducir el stock de la tienda online, hay un problema, devolvemos null
        if (stockOnline == null)
            return null;

        // Contruimos el pedido con los productos
        Pedido pedido = new Pedido(cliente);

        // Guardamos en la base de datos sin los productos
        pedido = pedidoDAO.save(pedido);

        // Establecemos los productos dentro del pedido
        pedido.setProductosYTotal(productosCarrito);

        // Guardamos en la base de datos con los productos
        pedido = pedidoDAO.save(pedido);

        // Guardamos los stocks correspondientes despues del pedido
        stockService.save(stockOnline);

        // Eliminamos los productos del carrito
        carritoService.eliminar(productosCarrito);

        // Devolvemos el pedido
        return pedido;
    }

    private List<StockProducto> reducirStockOnline(List<Carrito> productosCarrito)
    {
        // Inicializamos los atributos necesarios
        StockProductoId stockProductoId;
        StockProducto stockOnline;
        List<StockProducto> stockProductos = new ArrayList<>();
        long diferenciaStock;

        // Recorremos los productos del carrito
        for (Carrito carrito : productosCarrito)
        {
            // Creamos el id del stock del producto
            stockProductoId = new StockProductoId(idTiendaOnline, carrito.getId().getProductoId());

            // Obtenemos el stock del producto por su id
            stockOnline = stockService.findById(stockProductoId);

            // Si no se encuentra el stock, devolvemos false
            if (stockOnline == null)
                return null;

            // Realizamos la supuesta diferencia del stock del producto con lo del futuro pedido
            diferenciaStock = stockOnline.getStock() - carrito.getCantidad();

            // Si la diferencia es menor a cero, devolvemos false
            if (diferenciaStock < 0)
                return null;

            // Establecemos el nuevo stock y lo annadimos a la lista
            stockOnline.setStock(diferenciaStock);
            stockProductos.add(stockOnline);
        }

        return stockProductos;
    }
}
