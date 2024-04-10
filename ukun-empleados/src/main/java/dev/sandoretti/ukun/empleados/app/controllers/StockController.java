package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.StockProducto;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import dev.sandoretti.ukun.empleados.app.models.service.IStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;

import java.util.List;

@Controller
@SessionAttributes("empleado")
@RequestMapping("/stock")
public class StockController
{
    @Autowired
    private IStockService stockService;

    /**
     * Establece la lista de los productos con stock correspondientes a la tienda
     * @param model Model
     * @return Lista de productos con stock
     */
    @ModelAttribute
    public List<StockProducto> stockProductos(Model model)
    {
        Empleado empleado = (Empleado) model.getAttribute("empleado");
        Tienda tienda = empleado.getTienda();

        return stockService.findStockProductoByTienda(tienda);
    }

    @RequestMapping(value = {"", "/"})
    public String menu(Model model)
    {
        model.addAttribute("title", "Stock");
        return "stock";
    }
}
