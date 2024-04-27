package dev.sandoretti.ukun.clientes.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "direccion")
@Data @NoArgsConstructor @AllArgsConstructor
public class Direccion implements Serializable
{
    @Id
    @SequenceGenerator(name = "direccion_id_seq",
            sequenceName = "direccion_id_seq",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator="direccion_id_seq")
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