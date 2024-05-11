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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;

@Controller
@RequestMapping("/productos")
public class ProductosController extends AbsController
{
    @Autowired
    private IProductoService productoService;

    @Autowired
    private IStockService stockService;

    private final Tienda tiendaOnline;

    @Autowired
    public ProductosController(ITiendaService tiendaService) {
        // Consulta la tienda online y la guarda en el atributo tiendaOnline
        this.tiendaOnline = tiendaService.findByNombre("Ukun Online");
    }

    /**
     * Crea un HashMap que asigna las ids de los productos con su disponibilidad en la tienda dada
     * @param tienda Tienda a buscar
     * @return Hashmap de IDS de productos con boolean de la disponibilidad del producto
     */
    private HashMap<Long, Boolean> disponibilidadTienda(Tienda tienda)
    {
        // Obtenemos el stock de los productos de la tienda dada
        List<StockProducto> stockProductosOnline = stockService.findByTienda(tienda);

        // Inicializamos el hashmap de los productos
        HashMap<Long, Boolean> disponibleProductoHash = new HashMap<>();

        // Inicializamos parametros necesarios para la asignacion
        Long idProducto;
        boolean disponibleProducto;

        // Recorremos todos los stocks
        for (StockProducto stock : stockProductosOnline)
        {
            // Obtenemos el id del producto del stock
            idProducto = stock.getId().getProductoId();

            // Miramos si el producto esta disponible si el stock del producto es mayor a 0
            disponibleProducto = stock.getStock() > 0;

            // Lo metemos en el hasmap
            disponibleProductoHash.put(idProducto, disponibleProducto);
        }

        // Devolvemos el Hashmap
        return disponibleProductoHash;
    }

    @GetMapping({"", "/"})
    public String home(@ModelAttribute("cliente") Cliente cliente,
                       Model model)
    {
        // Obtenemos la lista de los productos
        List<Producto> productosList = productoService.findAll();

        // Obtenemos los stocks de los productos de la tienda online e inicializamos los de la favorita
        HashMap<Long, Boolean> disponibilidadFavorita = null,
                disponibilidadOnline = disponibilidadTienda(tiendaOnline);

        // Comprobamos que el cliente no se nulo
        if (cliente != null)
        {
            // Obtenemos la tienda favorita del cliente
            Tienda tiendaFav = cliente.getTienda();

            // Si la tienda no es nula, obtenemos el stock de los productos de la tienda favorita
            if (tiendaFav != null)
                disponibilidadFavorita = disponibilidadTienda(tiendaFav);
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

        // Inicializamos los stocks
        StockProducto stockOnline, stockFavorita = null;

        // Obtenemos el stock del producto en la tienda online
        stockOnline = stockService.findById(new StockProductoId(tiendaOnline.getId(), productoId));

        // Annadimos el stock del producto en la tienda online, si no tiene lo dejamos nulo
        model.addAttribute("stockOnline", stockOnline != null ? stockOnline.getStock() : null);

        // Comprobamos que el cliente no se nulo
        if (cliente != null)
        {
            // Obtenemos la tienda favorita del cliente
            Tienda tiendaFav = cliente.getTienda();

            // Si la tienda no es nula, obtenemos el stock del producto en la tienda favorita
            if (tiendaFav != null)
                stockFavorita = stockService.findById(new StockProductoId(tiendaFav.getId(), productoId));
        }

        // Annadimos el stock del producto en la tienda favorita, si no tiene lo dejamos nulo
        model.addAttribute("stockFavorita", stockFavorita != null ? stockFavorita.getStock() : null);

        return "ver-producto";
    }
}
