package dev.sandoretti.ukun.clientes.app.models.entity;

import dev.sandoretti.ukun.clientes.app.models.embeddable.StockProductoId;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name="stock_tienda")
@Data
public class StockProducto implements Serializable
{
    @EmbeddedId
    private StockProductoId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productoId")
    @JoinColumn(insertable = false, updatable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tiendaId")
    @JoinColumn(insertable = false, updatable = false)
    private Tienda tienda;

    @NotNull
    @Min(0)
    private Long stock;
}