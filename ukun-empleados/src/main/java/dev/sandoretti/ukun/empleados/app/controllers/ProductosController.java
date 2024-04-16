package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.entity.TipoProducto;
import dev.sandoretti.ukun.empleados.app.models.service.IProductoService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Controller
@RequestMapping("/admin/productos")
@SessionAttributes({"empleado", "producto"})
public class ProductosController
{
    @Autowired
    private IProductoService productoService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @ModelAttribute
    public List<Producto> productosList()
    {
        return productoService.findAll();
    }

    @ModelAttribute
    public List<TipoProducto> tipoProductosList()
    {
        return productoService.findAllTipoProductos();
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
        model.addAttribute("titulo", "Crear producto");

        return "guardarProducto";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id,
                         Model model,
                         RedirectAttributes flash)
    {
        Producto producto = productoService.findById(id);

        if (producto == null)
        {
            flash.addFlashAttribute("error", "No se ha encontrado el cliente con el id asociado");

            return "redirect:/admin/productos";
        }

        model.addAttribute("producto", producto);
        model.addAttribute("titulo", "Editar producto");

        return "guardarProducto";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("producto") Producto producto,
                          BindingResult result,
                          @RequestParam MultipartFile foto,
                          Model model,
                          RedirectAttributes flash)
    {
        // Comprobamos si no tiene errores de validacion
        if (result.hasErrors())
        {
            model.addAttribute("titulo", "Error en el formulario");

            return "guardarProducto";
        }

        // Miramos si tiene una foto asociada
        if (!foto.isEmpty())
        {
            // Creamos la ruta hacia la imagen dentro del servicio
            String pathFoto = producto.getNombre() + foto.getOriginalFilename();
            String absolutePathFoto = "/opt/resources/uploads/" + pathFoto;

            // TODO: Eliminar la foto anterior si el producto tiene una ruta asociada

            try
            {
                // Copiamos la foto en el servidor
                Files.copy(foto.getInputStream(), Path.of(absolutePathFoto));

                // Establecemos la nueva ruta hacia la foto
                producto.setPathFoto(pathFoto);
            }
            catch (IOException e)
            {
                log.error(e.toString());
            }
        }

        // Miramos si el producto ya existe en la base de datos
        String mensajeFlash = (producto.getId() != null) ? "editado" : "creado";

        // Guardamos el producto dentro de la base de datos
        productoService.save(producto);

        // Mandamos un mensaje de exito
        flash.addFlashAttribute("success", "Producto ".concat(mensajeFlash).concat(" con exito"));

        // Volvemos a la pantalla de productos
        return "redirect:/admin/productos";


    }
}
