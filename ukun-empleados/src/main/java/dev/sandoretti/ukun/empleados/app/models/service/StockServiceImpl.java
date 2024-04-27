package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IStockProductoCrud;
import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.empleados.app.models.entity.StockProducto;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockServiceImpl implements IStockService
{
    @Autowired
    private IStockProductoCrud stockProductoCrud;

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
}
