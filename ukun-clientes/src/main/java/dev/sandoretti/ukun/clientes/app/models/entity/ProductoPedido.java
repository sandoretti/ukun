package dev.sandoretti.ukun.clientes.app.models.entity;

import dev.sandoretti.ukun.clientes.app.models.embeddable.ProductoPedidoId;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "producto_pedido")
@Data @NoArgsConstructor @AllArgsConstructor
public class ProductoPedido implements Serializable
{
    @EmbeddedId
    private ProductoPedidoId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("pedidoId")
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", insertable = false, updatable = false)
    private Producto producto;

    @NotNull
    @Min(0)
    private Long cantidad;

    @NotNull
    @PositiveOrZero
    @Column(name = "precio_unitario", nullable = false)
    private Float precioUnidad;

    @NotNull
    @PositiveOrZero
    @Column(name = "precio_total", nullable = false)
    private Float precioTotal;

    public ProductoPedido(Pedido pedido, Producto producto, Long cantidad)
    {
        // Asignamos el producto y la cantidad
        this.pedido = pedido;
        this.producto = producto;
        this.cantidad = cantidad;

        this.id = new ProductoPedidoId(pedido.getId(), producto.getId());

        // Generamos el precio de unidad del producto
        this.precioUnidad = producto.getPrecio();

        // Generamos el precio total realizando la multiplicacion de cantidad por precio unidad
        this.precioTotal = this.precioUnidad * this.cantidad;
    }
}
