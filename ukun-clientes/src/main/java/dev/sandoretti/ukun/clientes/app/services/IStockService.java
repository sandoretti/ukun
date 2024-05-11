package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.clientes.app.models.entity.StockProducto;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;

import java.util.List;

public interface IStockService
{
    public List<StockProducto> findByTienda(Tienda tienda);
    public StockProducto findById(StockProductoId id);
    public void save(List<StockProducto> stockProducto);
}
