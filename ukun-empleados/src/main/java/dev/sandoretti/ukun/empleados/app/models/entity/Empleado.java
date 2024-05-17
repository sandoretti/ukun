package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name="empleado")
@Data
public class Empleado implements Serializable
{
    @Id
    private Long id;

    @Valid
    @OneToOne
    @JoinColumn(name = "id")
    @MapsId
    private Cuenta cuenta;

    @Column(name = "admin")
    private Boolean isAdmin;

    @NotBlank
    private String empleo;

    @OneToOne
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;


}
