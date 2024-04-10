package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name = "direccion")
@Data
public class Direccion implements Serializable
{
    @Id
    @SequenceGenerator(name = "direccion_id_seq",
            sequenceName = "direccion_id_seq",
            allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @NotEmpty
    private String calle;

    @NotEmpty
    private String provincia;

    @NotEmpty
    private String ciudad;

    @NotEmpty
    @Column(name = "codigo_postal")
    private String codigoPostal;
}
