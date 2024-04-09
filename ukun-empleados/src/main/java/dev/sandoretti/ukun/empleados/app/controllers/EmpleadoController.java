package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.service.IEmpleadoService;
import dev.sandoretti.ukun.empleados.app.models.service.ILoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@SessionAttributes("empleado")
@RequestMapping("/empleados")
public class EmpleadoController {
    @Autowired
    private ILoginService loginService;

    @RequestMapping(value = {"/", ""})
    public String home(Model model) {
        Empleado empleado = (Empleado) model.getAttribute("empleado");

        if (empleado != null && empleado.getId() != null && empleado.getId() > 0)
            return "index";

        return "redirect:/empleados/login";
    }

    @RequestMapping("/logout")
    public String logout(Model model)
    {
        model.addAttribute("empleado", new Empleado());
        return "redirect:/empleados/login";
    }

    @RequestMapping("/login")
    public String login(Model model)
    {
        Empleado empleado = (Empleado) model.getAttribute("empleado");

        // Comprobamos si el empleado esta ya logeado en la aplicacion
        // TODO: Realizar validacion del usuario en base de datos mediante el service
        if (empleado != null && empleado.getId() != null && empleado.getId() > 0)
            return "redirect:/empleados";

        return "login";
    }

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
            return "redirect:/empleados/login";
        }

        // Si los campos son vacios
        if (correo.isBlank() || contrasenna.isBlank())
        {
            flash.addFlashAttribute("error", "Alguno de los campos está vacío");
            return "redirect:/empleados/login";
        }

        // Buscamos el empleado mediante el servicio segun la cuenta dada
        Empleado empleado = loginService.loginEmpleado(correo, contrasenna);

        // Si el empleado no se ha encontrado devolvemos con error
        if (empleado == null)
        {
            flash.addFlashAttribute("error", "El correo o contraseña no corresponden con una cuenta existente");
            return "redirect:/empleados/login";
        }

        // Establecemos el empleado como atributo de la sesion
        model.addAttribute("empleado", empleado);

        // Volvemos a la ventana de inicio del empleado
        return "redirect:/empleados";
    }
}
