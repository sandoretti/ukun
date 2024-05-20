package dev.sandoretti.ukun.clientes.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.text.SimpleDateFormat;
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

    @NotBlank
    @Pattern(regexp = "\\d{16}", message = "El número de tarjeta debe contener exactamente 16 dígitos")
    private String numero;

    @NotNull
    @Column(name = "fecha_expiracion")
    @DateTimeFormat(pattern = "MM/yyyy")
    private Date fechaExpiracion;

    @NotBlank
    @Pattern(regexp = "\\d{3}", message = "El CVV debe contener exactamente 3 dígitos")
    private String cvv;

    public String fechaFormateada()
    {
        // Define el formato deseado
        SimpleDateFormat formatter = new SimpleDateFormat("MM/yyyy");

        return formatter.format(this.fechaExpiracion);
    }
}
