package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.service.ILoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@SessionAttributes("empleado")
@RequestMapping("/")
public class HomeController {
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
     * @param status Estado de la sesion
     * @return Pagina de login
     */
    @GetMapping("/logout")
    public String logout(SessionStatus status)
    {
        status.setComplete();
        return "redirect:/login";
    }

    /**
     * Pagina de inicio de sesion
     * @param model Modelo
     * @return Pagina html de inicio de sesion
     */
    @GetMapping("/login")
    public String login(Model model)
    {
        // Obtenemos el empleado del modelo
        Empleado empleado = (Empleado) model.getAttribute("empleado");

        // Comprobamos si el empleado esta ya logeado en la aplicacion
        if (empleado != null && empleado.getId() != null && empleado.getId() > 0)
            return "redirect:/";

        return "login";
    }

    /**
     * Inicia sesion dentro de la aplicacion con el correo y contrasenna
     * @param correo Correo del empleado
     * @param contrasenna Contrasenna del empleado
     * @param result Resultado
     * @param model Modelo
     * @param flash Atributos de redireccion
     * @return Redireccion correspondiente
     */
    @PostMapping("/login")
    public String login(@ModelAttribute("correo") String correo,
                        @ModelAttribute("contrasenna") String contrasenna,
                        BindingResult result,
                        Model model,
                        RedirectAttributes flash)
    {
        // Si el resultado tiene errores, devolvemos con un mensaje de error
        if (result.hasErrors())
        {
            flash.addFlashAttribute("error", "Error al iniciar sesión");
            return "redirect:/login";
        }

        // Si los campos son vacios
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
            return "redirect:/login";
        }

        // Establecemos el empleado como atributo de la sesion
        model.addAttribute("empleado", empleado);

        // Volvemos a la ventana de inicio del empleado
        return "redirect:/";
    }
}
