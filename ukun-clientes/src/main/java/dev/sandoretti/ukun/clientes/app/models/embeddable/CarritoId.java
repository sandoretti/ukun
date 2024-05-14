package dev.sandoretti.ukun.clientes.app.models.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data @NoArgsConstructor @AllArgsConstructor
public class CarritoId implements Serializable
{
    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "producto_id")
    private Long productoId;
}
