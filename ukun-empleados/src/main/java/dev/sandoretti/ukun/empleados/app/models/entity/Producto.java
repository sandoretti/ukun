package dev.sandoretti.ukun.empleados.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name="producto")
@Data
public class Producto implements Serializable
{
    @Id
    @SequenceGenerator(name = "producto_id_seq",
            sequenceName = "producto_id_seq",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator="producto_id_seq")
    @Column(name = "id", updatable = false)
    private Long id;

    @NotEmpty
    private String nombre;

    @NotNull
    @Min(0)
    private Float precio;

    @OneToOne
    @JoinColumn(name = "tipo")
    private TipoProducto tipo;

    @Column(name = "dir_foto")
    private String pathFoto;

    @NotEmpty
    private String descripcion;
}
