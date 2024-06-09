package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import dev.sandoretti.ukun.empleados.app.service.IEmpleadoService;
import dev.sandoretti.ukun.empleados.app.validators.EmpleadoValidator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
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

    @Autowired
    private EmpleadoValidator empleadoValidator;

    @InitBinder
    public void initBinder(WebDataBinder binder)
    {
        binder.addValidators(empleadoValidator);
    }

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
                        @ModelAttribute("empleado") Empleado administrador,
                        Model model,
                        RedirectAttributes flash)
    {
        // Comprobamos si la validacion del nuevo empleado es correcta
        if (result.hasErrors())
        {
            // En logs escribimos el error
            log.error("Hay errores al intentar validar el nuevo empleado: {}", result);

            // Volvemos a la ventana de crear empleado
            return "crearEmpleado";
        }

        // Obtenemos el correo del nuevo empleado
        String correoNuevoEmpleado = nuevoEmpleado.getCuenta().getCorreo().toLowerCase();

        // Cambiamos el correo a minusculas
        nuevoEmpleado.getCuenta().setCorreo(correoNuevoEmpleado);

        // Obtenemos la tienda del administrador y se la asignamos al nuevo empleado
        Tienda tienda = administrador.getTienda();
        nuevoEmpleado.setTienda(tienda);

        // Guardamos el nuevo empleado dentro de la base de datos
        empleadoService.save(nuevoEmpleado);

        // Devolvemos un mensaje de exito
        flash.addFlashAttribute("success", "Empleado creado con exito");

        // En logs escribimos el mensaje de exito
        log.info("Se ha insertado un nuevo empleado: {}", nuevoEmpleado);

        // Redirigimos a la pantalla principal de empleados
        return "redirect:/admin/empleados";
    }
}
