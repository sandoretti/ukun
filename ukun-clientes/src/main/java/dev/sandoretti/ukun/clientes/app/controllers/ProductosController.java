package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Producto;
import dev.sandoretti.ukun.clientes.app.models.entity.StockProducto;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import dev.sandoretti.ukun.clientes.app.services.IProductoService;
import dev.sandoretti.ukun.clientes.app.services.IStockService;
import dev.sandoretti.ukun.clientes.app.services.ITiendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;

@Controller
@RequestMapping("/productos")
public class ProductosController extends AbsController
{
    @Autowired
    private IProductoService productoService;

    @GetMapping({"", "/"})
    public String index(@RequestParam(required = false) String nombreProducto,
                        @RequestParam(required = false) Long tipo,
                        @RequestParam(required = false) Integer ordenPrecio,
                        @ModelAttribute("cliente") Cliente cliente,
                        Model model)
    {
        // Obtenemos la lista de los productos
        List<Producto> productosList = productoService.filtrarProductos(nombreProducto, tipo, ordenPrecio);

        // Obtenemos los stocks de los productos de la tienda online e inicializamos los de la favorita
        HashMap<Long, Boolean> disponibilidadFavorita = null,
                disponibilidadOnline = productoService.disponibilidadTiendaOnline(productosList);

        // Comprobamos que el cliente no es nulo
        if (cliente != null)
        {
            // Obtenemos la tienda favorita del cliente
            Tienda tiendaFav = cliente.getTienda();

            // Si la tienda no es nula, obtenemos el stock de los productos de la tienda favorita
            if (tiendaFav != null)
                disponibilidadFavorita = productoService.disponibilidadTienda(tiendaFav, productosList);
        }

        // Asignamos lo obtenido como atributos del modelo
        model.addAttribute("productosList", productosList);
        model.addAttribute("disponibilidadOnline", disponibilidadOnline);
        model.addAttribute("disponibilidadFavorita", disponibilidadFavorita);

        // Mandamos a la vista de productos.html
        return "productos";
    }

    @GetMapping("/ver/{idProducto}")
    public String ver(@PathVariable("idProducto") Long productoId,
                      @ModelAttribute("cliente") Cliente cliente,
                      RedirectAttributes flash,
                      Model model)
    {
        // Buscamos el producto en la base de datos segun el id
        Producto producto = productoService.findById(productoId);

        // Si no se ha encontrado el producto, lanzamos un error y redirigimos a /productos
        if (producto == null)
        {
            flash.addFlashAttribute("error", "El producto no existe");

            return "redirect:/productos";
        }

        // Annadimos el producto al modelo
        model.addAttribute("producto", producto);

        // Obtenemos el stock de la tienda online
        Long stockOnline = productoService.obtenerStockProductoTiendaOnline(producto);

        // Annadimos el stock del producto en la tienda online
        model.addAttribute("stockOnline", stockOnline);

        // Inicializamos el stock de la tienda favorita del cliente
        Long stockFavorita = null;

        // Comprobamos que el cliente no se nulo
        if (cliente != null)
        {
            // Obtenemos la tienda favorita del cliente
            Tienda tiendaFav = cliente.getTienda();

            // Si la tienda no es nula, obtenemos el stock del producto en la tienda favorita
            if (tiendaFav != null)
                stockFavorita = productoService.obtenerStockProductoTienda(tiendaFav, producto);
        }

        // Annadimos el stock del producto en la tienda favorita, si no tiene lo dejamos nulo
        model.addAttribute("stockFavorita", stockFavorita);

        return "ver-producto";
    }
}
