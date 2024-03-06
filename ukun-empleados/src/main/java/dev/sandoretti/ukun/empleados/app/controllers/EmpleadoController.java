package dev.sandoretti.ukun.empleados.app.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/empleados")
public class EmpleadoController {
    @RequestMapping(value = {"/", ""})
    public String home(Model model) {
        return "index";
    }
}
