package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import dev.sandoretti.ukun.clientes.app.services.IClienteService;
import dev.sandoretti.ukun.clientes.app.services.ITiendaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/perfil")
public class PerfilController extends AbsController
{
    @Autowired
    private IClienteService clienteService;

    @Autowired
    private ITiendaService tiendaService;

    @ModelAttribute
    public List<Tienda> tiendasList()
    {
        return tiendaService.findAll();
    }

    @GetMapping(value = {"", "/"})
    public String perfil(Model model)
    {
        return "perfil";
    }

    @PostMapping("/editar")
    public String editar(@Valid @ModelAttribute("cliente") Cliente clienteModificado,
                             BindingResult result,
                             Model model,
                             RedirectAttributes flash)
    {
        // Obtenenos el cliente correspondiente a la base de datos
        Cliente clienteActual = clienteService.findById(clienteModificado.getId());

        // Si el cliente es el mismo, volvemos a la pagina de perfil
        if (clienteActual.equals(clienteModificado))
        {
            return "redirect:/perfil";
        }

        // Comprobamos si no tiene errores de validacion, si lo tiene, redirigimos con mensaje de error
        if (result.hasErrors())
        {
            log.error("Errores: {}", result.getAllErrors());
            model.addAttribute("cliente", clienteActual);
            flash.addFlashAttribute("error", "Error al editar la cuenta");

            return "redirect:/perfil";
        }

        // Obtenemos los ids de los clientes
        Long idTiendaModificado = clienteModificado.getTienda().getId();
        Long idTiendaActual = clienteActual.getTienda().getId();

        // Comprobamos si los ids son diferentes, si lo son, cambiamos la tienda favorita
        if (!Objects.equals(idTiendaActual, idTiendaModificado))
        {
            clienteModificado.setTienda(tiendaService.findById(idTiendaModificado));
        }

        // Guardamos el cliente modificado en la base de datos
        clienteActual = clienteService.save(clienteModificado);

        // Guardamos el cliente como
        model.addAttribute("cliente", clienteActual);

        // Mandamos un mensaje de exito
        flash.addFlashAttribute("success", "Campos modificados con exito");
        log.info("Editando cliente: {}", clienteActual);

        return "redirect:/perfil";

    }
}
