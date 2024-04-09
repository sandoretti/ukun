package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name="empleado")
@Getter @Setter
public class Empleado implements Serializable
{
    @Id
    private Long id;

    @OneToOne
    @JoinColumn(name = "id")
    @MapsId
    private Cuenta cuenta;

    @Column(name = "admin")
    private String isAdmin;

    @NotEmpty
    private String empleo;

    @OneToOne
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;


}
