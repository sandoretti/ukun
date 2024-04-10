package dev.sandoretti.ukun.empleados.app.models.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Embeddable
@Data
public class StockProductoId implements Serializable
{
    @Column(name = "tienda_id")
    private Long tienda_id;

    @Column(name = "producto_id")
    private Long productoId;
}
