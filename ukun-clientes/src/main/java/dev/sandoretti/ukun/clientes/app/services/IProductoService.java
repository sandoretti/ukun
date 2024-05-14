package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.entity.Producto;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;

import java.util.HashMap;
import java.util.List;

public interface IProductoService
{
    /**
     * Obtiene el producto por su identificador
     * @param id Identificador del producto
     * @return Producto
     */
    public Producto findById(Long id);

    /**
     * Filtra los productos por los campos nombre y/o tipo de producto junto a si se quiere seguir el criterio del orden
     * del precio, si alguno de los campos es nulo o vacio, este devuelve la busqueda de todos los elementos
     * @param nombre Nombre del producto
     * @param idTipo Identificador del tipo de producto
     * @param ordenPrecio Orden del producto (0 = no ordendado, 1 = ascendente, 2 = descendente)
     * @return Lista de productos filtrados
     */
    public List<Producto> filtrarProductos(String nombre, Long idTipo, Integer ordenPrecio);

    /**
     * Crea un HashMap que asigna las ids de los productos con su disponibilidad en la tienda dada
     * @param tienda Tienda a buscar
     * @param productos Lista de productos
     * @return Hashmap de IDS de productos con boolean de la disponibilidad del producto
     */
    public HashMap<Long, Boolean> disponibilidadTienda(Tienda tienda, List<Producto> productos);

    /**
     * Crea un HashMap que asigna las ids de los productos con su disponibilidad en la tienda Ukun Online
     * @param productos Lista de productos
     * @return Hashmap de IDS de productos con boolean de la disponibilidad del producto
     */
    public HashMap<Long, Boolean> disponibilidadTiendaOnline(List<Producto> productos);

    /**
     * Obtiene el stock del producto en la tienda
     * @param tienda Tienda
     * @param producto Producto
     * @return Número del stock del producto en la tienda
     */
    public Long obtenerStockProductoTienda(Tienda tienda, Producto producto);

    /**
     * Obtiene el stock del producto en la tienda Ukun Online
     * @param producto Producto
     * @return Número del stock del producto en la tienda online
     */
    public Long obtenerStockProductoTiendaOnline(Producto producto);
}
