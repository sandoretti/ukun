package dev.sandoretti.ukun.clientes.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "tarjeta")
@Data @NoArgsConstructor @AllArgsConstructor
public class Tarjeta implements Serializable
{
    @Id
    @SequenceGenerator(name = "tarjeta_id_seq",
            sequenceName = "tarjeta_id_seq",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator="tarjeta_id_seq")
    private Long id;

    @NotEmpty
    @Size(min = 16, max = 16)
    @Pattern(regexp = "\\d{16}", message = "El número de tarjeta debe contener exactamente 16 dígitos")
    private String numero;

    @NotNull
    @Column(name = "fecha_expiracion")
    @DateTimeFormat(pattern = "MM/yyyy")
    private Date fechaExpiracion;

    @NotEmpty
    @Size(min = 3, max = 3)
    @Pattern(regexp = "\\d{3}", message = "El CVV debe contener exactamente 3 dígitos")
    private String cvv;
}