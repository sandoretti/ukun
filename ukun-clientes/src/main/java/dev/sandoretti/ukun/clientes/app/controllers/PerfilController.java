package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import dev.sandoretti.ukun.clientes.app.services.IClienteService;
import dev.sandoretti.ukun.clientes.app.services.ITiendaService;
import dev.sandoretti.ukun.clientes.app.utilities.validator.ClienteValidator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
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
    public String perfil(@ModelAttribute("cliente") Cliente cliente,
                         Model model)
    {
        Cliente clienteActualizado = clienteService.findById(cliente.getId());
        model.addAttribute("cliente", clienteActualizado);

        return "perfil";
    }

    @GetMapping("/editar")
    public String viewEditar(@ModelAttribute("cliente") Cliente cliente,
                             Model model)
    {
        Cliente clienteActualizado = clienteService.findById(cliente.getId());
        model.addAttribute("cliente", clienteActualizado);

        return "editar-perfil";
    }

    @PostMapping( "/editar")
    public String editar(@Valid @ModelAttribute("cliente") Cliente clienteModificado,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash)
    {
        // Comprobamos si no tiene errores de validacion, si lo tiene, redirigimos con mensaje de error
        if (result.hasErrors())
        {
            log.error("Errores: {}", result.getAllErrors());
            model.addAttribute("error", "Error al editar la cuenta");

            return "editar-perfil";
        }

        // Obtenenos el cliente correspondiente a la base de datos
        Cliente clienteActual = clienteService.findById(clienteModificado.getId());

        // Si el cliente es el mismo, volvemos a la pagina de perfil
        if (clienteActual.equals(clienteModificado))
        {
            model.addAttribute("info", "No se ha modificado ningún campo");
            return "editar-perfil";
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

        // Mandamos un mensaje de exito
        flash.addFlashAttribute("success", "Campos modificados con exito");
        log.info("Editando cliente: {}", clienteActual);

        return "redirect:/perfil";

    }
}
