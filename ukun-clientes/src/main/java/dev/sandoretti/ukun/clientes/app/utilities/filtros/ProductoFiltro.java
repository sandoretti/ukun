package dev.sandoretti.ukun.clientes.app.utilities.filtros;

import dev.sandoretti.ukun.clientes.app.models.entity.Producto;
import org.springframework.data.jpa.domain.Specification;

public class ProductoFiltro
{
    public static final int NO_ORDEN = 0;
    public static final int ASC = 1;
    public static final int DESC = 2;

    /**
     * Genera la Specification del producto para filtrar la busqueda por nombre
     * @param nombre Nombre del producto a buscar
     * @return Specification del producto para la busqueda
     */
    public static Specification<Producto> porNombre(String nombre) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")), "%" + nombre.toLowerCase() + "%");
    }

    /**
     * Genera la Specification del producto para filtrar la busqueda por tipo de producto
     * @param tipoId Identificador del tipo de producto
     * @return Specification del producto para la busqueda
     */
    public static Specification<Producto> porTipo(Long tipoId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("tipo").get("id"), tipoId);
    }
}
