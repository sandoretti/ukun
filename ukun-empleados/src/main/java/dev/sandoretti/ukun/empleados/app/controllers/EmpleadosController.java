package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import dev.sandoretti.ukun.empleados.app.models.service.IEmpleadoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/empleados")
@SessionAttributes({"empleado", "nuevoEmpleado"})
public class EmpleadosController extends AbsController
{
    @Autowired
    private IEmpleadoService empleadoService;

    @ModelAttribute
    public Empleado empleado(Model model)
    {
        return (Empleado) model.getAttribute("empleado");
    }

    @GetMapping(value = {"/", ""})
    public String index(Model model)
    {
        Empleado empleado = empleado(model);
        List<Empleado> empleados = empleadoService.findByTienda(empleado.getTienda());

        model.addAttribute("empleadosList", empleados);
        return "empleados";
    }

    @GetMapping(value = "/crear")
    public String crear(Model model)
    {
        model.addAttribute("nuevoEmpleado", new Empleado());

        return "crearEmpleado";
    }

    @PostMapping("/crear")
    public String crear(@Valid @ModelAttribute("nuevoEmpleado") Empleado nuevoEmpleado,
                        BindingResult result,
                        Model model,
                        RedirectAttributes flash)
    {
        // Comprobamos si la validacion del nuevo empleado es correcta
        if (result.hasErrors())
            return "crearEmpleado";

        // Obtenemos el correo del nuevo empleado
        String correoNuevoEmpleado = nuevoEmpleado.getCuenta().getCorreo().toLowerCase();

        // Miramos si el correo existe y si existe, lanzamos un error volviendo a la pagina
        if (empleadoService.validarCorreo(correoNuevoEmpleado))
        {
            model.addAttribute("error", "El correo ya existe");

            return "crearEmpleado";
        }

        // Cambiamos el correo a minusculas
        nuevoEmpleado.getCuenta().setCorreo(correoNuevoEmpleado);

        // Obtenemos el adminstrador
        Empleado administrador = empleado(model);

        // Obtenemos la tienda del administrador y se la asignamos al nuevo empleado
        Tienda tienda = administrador.getTienda();
        nuevoEmpleado.setTienda(tienda);

        // Guardamos el nuevo empleado dentro de la base de datos
        empleadoService.save(nuevoEmpleado);

        // Devolvemos un mensaje de exito
        flash.addFlashAttribute("success", "Empleado creado con exito");

        // Redirigimos a la pantalla principal de empleados
        return "redirect:/admin/empleados";
    }
}
