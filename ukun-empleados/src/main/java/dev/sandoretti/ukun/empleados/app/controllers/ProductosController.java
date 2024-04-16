package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.entity.TipoProducto;
import dev.sandoretti.ukun.empleados.app.models.service.IProductoService;
import dev.sandoretti.ukun.empleados.app.models.service.IUploadFileService;
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
import java.util.List;

@Controller
@RequestMapping("/admin/productos")
@SessionAttributes({"empleado", "producto"})
public class ProductosController
{
    @Autowired
    private IProductoService productoService;

    @Autowired
    private IUploadFileService uploadFileService;

    private static final Logger log = LoggerFactory.getLogger(ProductosController.class);

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
            // Obtenemos el path de la foto del producto
            String pathFoto = producto.getPathFoto();

            // Comprobamos si el producto tiene una foto asociada
            if (pathFoto != null && !pathFoto.isEmpty())
            {
                // Eliminamos la foto de la carpeta uploads
                if (!uploadFileService.delete(pathFoto))
                    log.info("Se ha eliminado la foto: ".concat(pathFoto));
            }

            // Cargamos la nueva foto en la carpeta
            try {
                pathFoto = uploadFileService.copy(foto, producto.getNombre());
            } catch (IOException e) {
                log.error("Hubo un error al copiar la foto: ", e);
            }

            // Establecemos la ruta de la nueva foto
            producto.setPathFoto(pathFoto);
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
