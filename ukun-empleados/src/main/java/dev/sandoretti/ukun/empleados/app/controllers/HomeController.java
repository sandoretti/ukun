package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.service.ILoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@SessionAttributes("empleado")
@RequestMapping("/")
public class HomeController extends AbsController{
    @Autowired
    private ILoginService loginService;

    /**
     * Pagina principal de la aplicacion
     * @return Pagina html principal
     */
    @GetMapping(value = {"/", ""})
    public String home(@ModelAttribute("empleado") Empleado empleado)
    {
        return "index";
    }

    /**
     * Cierra sesion al empleado dentro de la aplicacion
     * @param empleado Empleado a cerrar sesion
     * @param status Estado de la sesion
     * @return Pagina de login
     */
    @GetMapping("/logout")
    public String logout(@ModelAttribute("empleado") Empleado empleado,
                         SessionStatus status)
    {
        status.setComplete();
        log.info("Se ha cerrado la sesión con el empleado de id: {}", empleado.getId());
        return "redirect:/login";
    }

    /**
     * Pagina de inicio de sesion
     * @return Pagina html de inicio de sesion
     */
    @GetMapping("/login")
    public String login()
    {
        return "login";
    }

    /**
     * Inicia sesion dentro de la aplicacion con el correo y contrasenna
     * @param correo Correo del empleado
     * @param contrasenna Contrasenna del empleado
     * @param model Modelo
     * @param flash Atributos de redireccion
     * @return Redireccion correspondiente
     */
    @PostMapping("/login")
    public String login(@ModelAttribute("correo") String correo,
                        @ModelAttribute("contrasenna") String contrasenna,
                        Model model,
                        RedirectAttributes flash)
    {
        // Si los campos son vacios mandamos un mensaje de error
        if (correo.isBlank() || contrasenna.isBlank())
        {
            flash.addFlashAttribute("error", "Alguno de los campos está vacío");
            return "redirect:/login";
        }

        // Buscamos el empleado mediante el servicio segun la cuenta dada
        Empleado empleado = loginService.loginEmpleado(correo, contrasenna);

        // Si el empleado no se ha encontrado devolvemos con error
        if (empleado == null)
        {
            flash.addFlashAttribute("error", "El correo o contraseña no corresponden con una cuenta existente");
            log.error("Se ha intentado acceder como empleado con el correo: {} y contrasenna: {}.", correo, contrasenna);

            return "redirect:/login";
        }

        // Establecemos el empleado como atributo de la sesion
        model.addAttribute("empleado", empleado);

        // Escribimos un mensaje en logs
        log.info("Ha iniciado sesion el empleado con id: {}", empleado.getId());

        // Volvemos a la ventana de inicio del empleado
        return "redirect:/";
    }
}
