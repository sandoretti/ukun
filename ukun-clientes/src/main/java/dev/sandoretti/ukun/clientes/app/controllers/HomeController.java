package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;

@Controller
@SessionAttributes("cliente")
@RequestMapping("/")
public class HomeController
{
    @ModelAttribute
    private Cliente cliente(Model model)
    {
        return (Cliente) model.getAttribute("cliente");
    }

    @GetMapping(value = {"/", ""})
    public String home(Model model)
    {
        return "index";
    }
}
