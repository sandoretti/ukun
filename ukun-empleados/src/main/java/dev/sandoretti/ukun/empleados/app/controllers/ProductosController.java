package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.service.IProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/productos")
@SessionAttributes("empleado")
public class ProductosController
{
    @Autowired
    private IProductoService productoService;

    @ModelAttribute
    public List<Producto> productosList()
    {
        return productoService.findAll();
    }

    @GetMapping(value = {"", "/"})
    public String index()
    {
        return "productos";
    }
}
