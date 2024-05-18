package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IProductoCrud;
import dev.sandoretti.ukun.empleados.app.models.dao.IStockProductoCrud;
import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.entity.StockProducto;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class StockServiceImpl implements IStockService
{
    @Autowired
    private IStockProductoCrud stockProductoCrud;

    @Autowired
    private IProductoCrud productoCrud;

    @Override
    @Transactional(readOnly = true)
    public List<StockProducto> findStockProductoByTienda(Tienda tienda)
    {
        return stockProductoCrud.findByTienda(tienda);
    }

    @Override
    @Transactional
    public void delete(StockProductoId id) {
        stockProductoCrud.deleteById(id);
    }

    @Override
    @Transactional
    public void save(StockProducto stockProducto) {
        stockProductoCrud.save(stockProducto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> findProductoNotInTienda(Tienda tienda)
    {
        return stockProductoCrud.findProductoNotInTienda(tienda);
    }

    @Override
    @Transactional
    public boolean guardar(Tienda tienda, Long idProduto, Long stock)
    {
        Producto producto = productoCrud.findById(idProduto).orElse(null);

        if (producto == null)
            return false;

        StockProducto stockProducto = new StockProducto();

        stockProducto.setId(new StockProductoId(tienda.getId(), producto.getId()));
        stockProducto.setStock(stock);
        stockProducto.setTienda(tienda);
        stockProducto.setProducto(producto);

        return !stockProductoCrud.save(stockProducto).equals(Optional.empty());
    }
}
