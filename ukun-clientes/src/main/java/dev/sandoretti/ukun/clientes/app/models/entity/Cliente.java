package dev.sandoretti.ukun.clientes.app.models.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "cliente")
@Data @NoArgsConstructor @AllArgsConstructor
public class Cliente implements Serializable
{
    @Id
    private Long id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id")
    @MapsId
    @Valid
    private Cuenta cuenta;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id_direccion")
    @Valid
    private Direccion direccion;

    @ManyToOne
    @JoinColumn(name = "tienda_fav")
    private Tienda tienda;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id_tarjeta")
    @Valid
    private Tarjeta tarjeta;
}
