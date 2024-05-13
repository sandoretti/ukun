package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.entity.Producto;

import java.util.List;

public interface IProductoService
{
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
}
