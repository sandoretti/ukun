package dev.sandoretti.ukun.clientes.app.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping(value = {"/", ""})
    public String home(Model model){
        return "index";
    }
}
