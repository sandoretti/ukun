package dev.sandoretti.ukun.clientes.app.models.entity;

import dev.sandoretti.ukun.clientes.app.models.embeddable.CarritoId;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name="carrito")
@Data @NoArgsConstructor @AllArgsConstructor
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
