package dev.sandoretti.ukun.clientes.app.models.dao;

import dev.sandoretti.ukun.clientes.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.clientes.app.models.entity.StockProducto;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface IStockProductoDAO extends CrudRepository<StockProducto, StockProductoId>
{
    public List<StockProducto> findStockProductoByTienda(Tienda tienda);
}
