package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.service.IProductoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/productos")
@SessionAttributes({"empleado", "producto"})
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

    @GetMapping("/crear")
    public String crear(Model model)
    {
        model.addAttribute("producto", new Producto());
        return "guardarProducto";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("producto") Producto producto,
                          @RequestParam MultipartFile foto,
                          BindingResult result,
                          Model model,
                          RedirectAttributes flash)
    {
        if (result.hasErrors())
        {
            flash.addFlashAttribute("error", "Hubo un error al guardar el producto");
            return "guardarProducto";
        }

        return "redirect:/admin/productos";


    }
}
