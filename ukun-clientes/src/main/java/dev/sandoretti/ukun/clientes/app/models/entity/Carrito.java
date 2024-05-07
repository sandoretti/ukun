package dev.sandoretti.ukun.clientes.app.models.entity;

import dev.sandoretti.ukun.clientes.app.models.embeddable.CarritoId;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name="carrito")
@Data
public class Carrito implements Serializable
{
    @EmbeddedId
    private CarritoId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("clienteId")
    @JoinColumn
    private Cliente cliente;

    @ManyToOne
    @MapsId("productoId")
    @JoinColumn
    private Producto producto;

    @NotNull
    @Min(0)
    private Long cantidad;
}
