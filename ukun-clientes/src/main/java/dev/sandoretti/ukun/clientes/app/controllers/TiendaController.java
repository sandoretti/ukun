package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import dev.sandoretti.ukun.clientes.app.services.ITiendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/tiendas")
public class TiendaController extends AbsController
{
    @Autowired
    private ITiendaService tiendaService;

    @RequestMapping({"", "/"})
    public String home(Model model)
    {
        List<Tienda> tiendaList = tiendaService.findAll();

        model.addAttribute("tiendas", tiendaList);

        return "tiendas";
    }
}
