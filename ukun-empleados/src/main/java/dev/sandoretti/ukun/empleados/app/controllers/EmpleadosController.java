package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.service.IEmpleadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/empleados")
@SessionAttributes("empleado")
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
}
