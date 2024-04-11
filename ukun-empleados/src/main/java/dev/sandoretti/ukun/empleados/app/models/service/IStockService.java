package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.empleados.app.models.entity.StockProducto;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;

import java.util.List;

public interface IStockService
{
    /**
     * Obtiene todos los productos junto a los stocks correspondientes de la tienda
     * @param tienda Tienda del stock a buscar
     * @return Lista de los productos junto a stock
     */
    public List<StockProducto> findStockProductoByTienda(Tienda tienda);

    /**
     * Elimina el stock producto de la base de datos
     * @param id Identificador del stock producto a eliminar
     */
    public void delete(StockProductoId id);
}
