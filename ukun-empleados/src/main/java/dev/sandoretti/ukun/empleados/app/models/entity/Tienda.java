package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "tienda")
@Getter @Setter
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tienda tienda = (Tienda) o;
        return Objects.equals(id, tienda.id);
    }
}
