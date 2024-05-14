package dev.sandoretti.ukun.clientes.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
@Data @NoArgsConstructor @AllArgsConstructor
public class Pedido implements Serializable
{
    @Id
    @SequenceGenerator(name = "pedido_id_seq",
            sequenceName = "pedido_id_seq",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator="pedido_id_seq")
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direccion_id", nullable = false)
    private Direccion direccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarjeta_id", nullable = false)
    private Tarjeta tarjeta;

    @NotNull
    private LocalDateTime fecha;

    @NotNull
    @PositiveOrZero
    private Float total;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductoPedido> productos;

    public Pedido(Cliente cliente)
    {
        // Annadimos el cliente
        this.cliente = cliente;

        // Obtnemos la direccion y la tajeta del cliente
        this.direccion = cliente.getDireccion();
        this.tarjeta = cliente.getTarjeta();

        // Obtenemos la fecha actual al realizar el pedido y el total a 0
        this.fecha = LocalDateTime.now();
        this.total = 0F;
        this.productos = new ArrayList<>();
    }

    public void setProductosYTotal(List<Carrito> productosCarrito)
    {
        this.productos.clear();

        // Total a 0
        Float totalProductos = 0F;

        // Recorremos todos los productos del carrito
        for (Carrito carrito : productosCarrito)
        {
            // Creamos el producto del pedido a partir del producto del carrito
            ProductoPedido productoPedido = new ProductoPedido(this, carrito.getProducto(), carrito.getCantidad());

            // Annadimos el producto pedido a la lista
            this.productos.add(productoPedido);

            // Sumamos el total del producto al precio total del pedido
            totalProductos += productoPedido.getPrecioTotal();
        }

        this.total = totalProductos;
    }
}
