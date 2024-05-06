package dev.sandoretti.ukun.clientes.app.models.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Embeddable
@Data
public class StockProductoId implements Serializable
{
    @Column(name = "tienda_id")
    private Long tiendaId;

    @Column(name = "producto_id")
    private Long productoId;
}