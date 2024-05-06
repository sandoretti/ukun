package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Producto;
import dev.sandoretti.ukun.clientes.app.models.entity.StockProducto;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import dev.sandoretti.ukun.clientes.app.services.IProductoService;
import dev.sandoretti.ukun.clientes.app.services.IStockService;
import dev.sandoretti.ukun.clientes.app.services.ITiendaService;
import dev.sandoretti.ukun.clientes.app.services.TiendaServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

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

    @Autowired
    private ITiendaService tiendaService;

    @ModelAttribute("productosList")
    public List<Producto> productosList()
    {
        return productoService.findAll();
    }

    @ModelAttribute("disponibilidadOnline")
    public HashMap<Long, Boolean> disponibilidadOnline()
    {
        Tienda tiendaOnline = tiendaService.findByNombre("Ukun Online");

        return disponibilidadTienda(tiendaOnline);
    }

    private HashMap<Long, Boolean> disponibilidadTienda(Tienda tienda)
    {
        List<StockProducto> stockProductosOnline = stockService.findByTienda(tienda);
        HashMap<Long, Boolean> disponibleProductoHash = new HashMap<>();

        Long idProducto;
        boolean disponibleProducto;

        for (StockProducto stock : stockProductosOnline)
        {
            idProducto = stock.getProducto().getId();
            disponibleProducto = stock.getStock() > 0;

            disponibleProductoHash.put(idProducto, disponibleProducto);
        }

        return disponibleProductoHash;
    }

    @GetMapping({"", "/"})
    public String home(@ModelAttribute("cliente") Cliente cliente,
                       Model model)
    {
        HashMap<Long, Boolean> disponibilidadFavorita = null;

        if (cliente != null)
        {
            Tienda tiendaFav = cliente.getTienda();

            if (tiendaFav != null)
                disponibilidadFavorita = disponibilidadTienda(tiendaFav);
        }


        model.addAttribute("disponibilidadFavorita", disponibilidadFavorita);

        return "productos";
    }
}
