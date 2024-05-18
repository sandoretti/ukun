package dev.sandoretti.ukun.empleados.app.models.dao;

import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.entity.StockProducto;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface IStockProductoCrud extends CrudRepository<StockProducto, StockProductoId>
{
    @Query("SELECT sp FROM StockProducto sp WHERE sp.tienda = ?1")
    public List<StockProducto> findByTienda(Tienda tienda);

    @Query("SELECT p FROM Producto p WHERE p.id NOT IN (SELECT sp.id.productoId FROM StockProducto sp WHERE sp.tienda = ?1)")
    public List<Producto> findProductoNotInTienda(Tienda tienda);
}
