package dev.sandoretti.ukun.empleados.app.models.entity;

import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name="producto")
@Data
public class StockProducto implements Serializable
{
    @EmbeddedId
    private StockProductoId id;

    @ManyToOne
    @MapsId("producto_id")
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tienda_id")
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @NotNull
    @Min(0)
    private Long stock;
}
