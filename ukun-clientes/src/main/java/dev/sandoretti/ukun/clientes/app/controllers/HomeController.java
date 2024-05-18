package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.services.ILoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/")
public class HomeController extends AbsController
{
    @Autowired
    private ILoginService loginService;

    @GetMapping(value = {"/", ""})
    public String home()
    {
        return "index";
    }

    @GetMapping("/login")
    public String login()
    {
        return "login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute("correo") String correo,
                        @ModelAttribute("contrasenna") String contrasenna,
                        Model model,
                        RedirectAttributes flash)
    {
        // Si los campos son vacios
        if (correo.isBlank() || contrasenna.isBlank())
        {
            flash.addFlashAttribute("error", "Alguno de los campos está vacío");
            return "redirect:/login";
        }

        Cliente cliente = loginService.login(correo, contrasenna);

        if (cliente == null)
        {
            flash.addFlashAttribute("error", "El correo o contraseña no corresponden con una cuenta existente");
            return "redirect:/login";
        }

        // Establecemos el empleado como atributo de la sesion
        model.addAttribute("cliente", cliente);

        // Volvemos a la ventana de inicio del empleado
        return "redirect:/";
    }

    /**
     * Cierra sesion al cliente
     * @param status Estado de la sesion
     * @return Pagina principal
     */
    @GetMapping("/logout")
    public String logout(SessionStatus status)
    {
        status.setComplete();
        return "redirect:/";
    }
}
