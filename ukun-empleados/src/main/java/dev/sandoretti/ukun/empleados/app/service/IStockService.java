package dev.sandoretti.ukun.empleados.app.service;

import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
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
     * @param idProducto Identificador del producto del stock
     * @param idTienda Identificador de la tienda del stock
     */
    public void delete(Long idProducto, Long idTienda);

    /**
     * Guardar el stock producto en la base de datos
     * @param stockProducto Stock producto a guardar
     */
    public void save(StockProducto stockProducto);

    /**
     * Productos que no se encuentran dentro del stock de la tienda
     * @param tienda Tienda a que corresponde
     * @return
     */
    public List<Producto> findProductoNotInTienda(Tienda tienda);

    /**
     * Guarda el stock dentro de la base de datos
     * @param tienda Tienda dada
     * @param idProduto Id del producto
     * @param stock Stock del producto
     * @return Si se ha insertado o no
     */
    public boolean guardar(Tienda tienda, Long idProduto, Long stock);
}
