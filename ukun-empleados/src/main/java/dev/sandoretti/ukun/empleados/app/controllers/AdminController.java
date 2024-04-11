package dev.sandoretti.ukun.empleados.app.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;

@Controller
@RequestMapping("/admin")
@SessionAttributes("empleado")
public class AdminController
{
    @GetMapping(value = {"", "/"})
    public String index(Model model)
    {
        return "admin";
    }
}
