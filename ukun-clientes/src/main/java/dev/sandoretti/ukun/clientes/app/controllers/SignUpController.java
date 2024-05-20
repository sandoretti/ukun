package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import dev.sandoretti.ukun.clientes.app.services.ISignUpService;
import dev.sandoretti.ukun.clientes.app.services.ITiendaService;
import dev.sandoretti.ukun.clientes.app.utilities.validator.ClienteValidator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@SessionAttributes("clienteNuevo")
@RequestMapping("/signup")
public class SignUpController
{
    @Autowired
    private ISignUpService signUpService;

    @Autowired
    private ITiendaService tiendaService;

    @Autowired
    private ClienteValidator clienteValidator;

    @ModelAttribute
    public List<Tienda> tiendasList()
    {
        return tiendaService.findAll();
    }

    @InitBinder
    public void initBinder(WebDataBinder binder)
    {
        binder.addValidators(clienteValidator);
    }

    @GetMapping({"", "/"})
    public String index(Model model)
    {
        Cliente clienteNuevo = new Cliente();

        model.addAttribute("clienteNuevo", clienteNuevo);

        return "signup";
    }

    @PostMapping({"", "/"})
    public String registrar(@Valid @ModelAttribute("clienteNuevo") Cliente clienteNuevo,
                            BindingResult result,
                            Model model,
                            SessionStatus status,
                            RedirectAttributes flash)
    {
        // Si existen errores en los campos, devuelve error a la pagina
        if (result.hasErrors())
        {
            model.addAttribute("error", "Hubo un error al registrar el cliente");

            return "signup";
        }

        // Eliminamos el cliente nuevo dentro de la sesion
        status.setComplete();

        // Registramos la cuenta dentro de la base de datos
        signUpService.registrar(clienteNuevo);

        // Devolvemos el mensaje de informacion y redirigimos a la pagina principal
        flash.addFlashAttribute("success", "El cliente se ha registrado correctamente");
        return "redirect:/";
    }
}
