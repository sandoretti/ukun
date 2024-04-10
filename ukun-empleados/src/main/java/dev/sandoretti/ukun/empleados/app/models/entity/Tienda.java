package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name = "tienda")
@Data
public class Tienda implements Serializable
{
    @Id
    @SequenceGenerator(name = "tienda_id_seq",
            sequenceName = "tienda_id_seq",
            allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @NotEmpty
    private String nombre;

    @OneToOne
    @JoinColumn(name = "direccion_id")
    private Direccion direccion;

    @NotEmpty
    private String telefono;
}
