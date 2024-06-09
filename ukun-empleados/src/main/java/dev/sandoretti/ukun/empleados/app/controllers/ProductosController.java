package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Producto;
import dev.sandoretti.ukun.empleados.app.models.entity.TipoProducto;
import dev.sandoretti.ukun.empleados.app.service.IProductoService;
import dev.sandoretti.ukun.empleados.app.service.IUploadFileService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
public class ProductosController extends AbsController
{
    @Autowired
    private IProductoService productoService;

    @Autowired
    private IUploadFileService uploadFileService;

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

    @GetMapping("/editar/{idProducto}")
    public String editar(@PathVariable(name = "idProducto") Long id,
                         Model model,
                         RedirectAttributes flash)
    {
        // Obtenemos el producto a partir del id del producto
        Producto producto = productoService.findById(id);

        // Verificamos si el producto existe dentro de la base de datos, sino lanzamos un error
        if (producto == null)
        {
            // Lanzamos un mensaje de error
            flash.addFlashAttribute("error", "No se ha encontrado el cliente con el id asociado");

            // En logs escribimos el error
            log.error("Se ha intentado acceder a la vista de edición de un producto inexistente con el id: {}", id);

            // Volvemos a la pantalla de productos
            return "redirect:/admin/productos";
        }

        // Annadimos los atributos de producto y titulo
        model.addAttribute("producto", producto);
        model.addAttribute("titulo", "Editar producto");

        // Devolvemos la vista
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
            // Cambiamos el titulo del formulario
            model.addAttribute("titulo", "Error en el formulario");

            // En logs escribimos el error
            log.error("Hay errores al intentar validar el producto: {}", result);

            // Devolvemos la vista
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
                    log.info("Se ha eliminado la foto: {}", pathFoto);
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

        // En logs escribimos el mensaje de exito
        log.info("Se ha ".concat(mensajeFlash).concat(" el producto: {}"), producto);

        // Volvemos a la pantalla de productos
        return "redirect:/admin/productos";
    }

    @GetMapping("/eliminar/{idProducto}")
    public String eliminar(@PathVariable(name = "idProducto") Long idProducto,
                           RedirectAttributes flash)
    {
        // Obtenenmos el producto a partir del id
        Producto producto = productoService.findById(idProducto);

        // Si el producto es nulo, no exite dentro de la base de datos
        if (producto == null)
        {
            // Devolvemos un mensaje de error
            flash.addFlashAttribute("error", "No existe el producto.");

            // En logs escribimos el error
            log.error("Se ha intentado eliminar un producto inexistente con el id: {}", idProducto);

            // Volvemos a la pantalla de productos
            return "redirect:/admin/productos";
        }

        try
        {
            // Eliminamos el producto a partir de su id
            productoService.deleteById(idProducto);

            // Eliminamos la ruta de la foto vinculada al producto de la carpeta uploads
            uploadFileService.delete(producto.getPathFoto());

            // Mandamos un mensaje de exito
            flash.addFlashAttribute("success", "Producto eliminado con exito.");
            log.info("Se ha eliminado el producto: {}", producto);
        }
        // Si se ha detectado un error porque el producto depende de otras tablas
        catch (DataIntegrityViolationException e)
        {
            // Mandamos un error por los logs
            log.error("Se ha intentado eliminar el producto con id {} pero este depende de otras tablas ", idProducto);

            // Mandamos un mensaje de error a la vista
            flash.addFlashAttribute("error", "El producto no puede ser eliminado, este depende de otros.");
        }

        // Volvemos a la pantalla de productos
        return "redirect:/admin/productos";
    }
}
