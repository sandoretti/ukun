package dev.sandoretti.ukun.clientes.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "tipo_producto")
@Getter
@Setter
public class TipoProducto implements Serializable
{
    @Id
    @SequenceGenerator(name = "tipo_producto_id_seq",
            sequenceName = "tipo_producto_id_seq",
            allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @NotEmpty
    private String nombre;
}
