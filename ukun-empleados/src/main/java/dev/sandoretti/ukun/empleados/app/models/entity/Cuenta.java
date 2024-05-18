package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator="cuenta_id_seq")
    @Column(name = "id", updatable = false)
    private Long id;

    @NotBlank
    @Email
    @Column(unique = true)
    private String correo;

    @NotBlank
    private String contrasenna;

    @NotBlank
    private String nombre;

    @NotBlank
    private String apellido;

}
