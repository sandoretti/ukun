package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.services.IClienteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/perfil")
public class PerfilController extends AbsController
{
    @Autowired
    private IClienteService clienteService;

    @GetMapping(value = {"", "/"})
    public String perfil()
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
            flash.addFlashAttribute("org.springframework.validation.BindingResult.cliente", result);
            flash.addFlashAttribute("error", "Error al editar la cuenta");

            model.addAttribute("cliente", clienteActual);

            return "redirect:/perfil";
        }

        // Guardamos el cliente modificado en la base de datos
        clienteService.save(clienteModificado);

        // Mandamos un mensaje de exito
        flash.addFlashAttribute("success", "Campos modificados con exito");

        return "redirect:/perfil";

    }
}
