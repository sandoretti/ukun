package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name = "cuenta")
@Data
public class Cuenta implements Serializable
{
    @Id
    @SequenceGenerator(name = "cuenta_id_seq",
                       sequenceName = "cuenta_id_seq",
                       allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @NotEmpty
    @Email
    @Column(unique = true)
    private String correo;

    @NotEmpty
    private String contrasenna;

    @NotEmpty
    private String nombre;

    @NotEmpty
    private String apellido;

}
