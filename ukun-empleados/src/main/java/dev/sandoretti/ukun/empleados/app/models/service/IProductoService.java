package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.entity.TipoProducto;

import java.util.List;

public interface IProductoService
{
    /**
     * Obtiene el producto a partir de su id
     * @param id Identificador del producto
     * @return Producto
     */
    public Producto findById(Long id);

    /**
     * Devuelve una lista de todos los productos de la base de datos
     * @return Lista de productos
     */
    public List<Producto> findAll();

    /**
     * Devuelve una lista con todos los tipos de productos
     * @return Lista de tipos de productos
     */
    public List<TipoProducto> findAllTipoProductos();

    /**
     * Guarda el producto dado dentro de la base de datos, ya sea nuevo o editado
     * @param producto Producto a guardar
     */
    public void save(Producto producto);
}
