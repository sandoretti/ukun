package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.StockProducto;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import dev.sandoretti.ukun.empleados.app.models.service.IStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;

@Controller
@SessionAttributes("empleado")
@RequestMapping("/stock")
public class StockController
{
    @Autowired
    private IStockService stockService;

    /**
     * Establece la tienda correspondiente al usuario
     * @param model Modelo
     * @return Tienda del empleado
     */
    @ModelAttribute("tienda")
    public Tienda tienda(Model model)
    {
        Empleado empleado = (Empleado) model.getAttribute("empleado");
        return empleado.getTienda();
    }

    /**
     * Establece la lista de los productos con stock correspondientes a la tienda
     * @param model Model
     * @return Lista de productos con stock
     */
    @ModelAttribute("stockProductosList")
    public List<StockProducto> stockProductos(Model model)
    {
        Tienda tienda = (Tienda) model.getAttribute("tienda");

        return stockService.findStockProductoByTienda(tienda);
    }

    @GetMapping(value = {"", "/"})
    public String menu()
    {
        return "stock";
    }

    /**
     * Eliminar el stockProducto de la base de datos
     * @param tiendaId Identificador de la tienda
     * @param productoId Identificador del producto
     * @param model Modelo
     * @param flash Atributos de redireccion
     * @return Redireccion
     */
    @GetMapping("/eliminar")
    public String eliminar(@RequestParam Long tiendaId,
                           @RequestParam Long productoId,
                           Model model,
                           RedirectAttributes flash)
    {
        // Comprobamos si los parametros no son nulos, si no lo son mandamos a stock con mensaje de error
        if (tiendaId == null || productoId == null)
        {
            flash.addFlashAttribute("error", "Los parametros insertados estan vacios");
            return "redirect:/stock";
        }

        // Comprobamos que el id de la tienda es igual que el id pasado, si no lo son mandamos a stock con mensaje de error
        if (!Objects.equals(tienda(model).getId(), tiendaId))
        {
            flash.addFlashAttribute("error", "No pertenece a la tienda correspondiente");
            return "redirect:/stock";
        }

        // Creamos el id de stock producto con los campos requeridos
        StockProductoId stockProductoId = new StockProductoId();

        stockProductoId.setProductoId(productoId);
        stockProductoId.setTiendaId(tiendaId);

        // Eliminamos el stock del producto
        stockService.delete(stockProductoId);

        // Mandamos mensaje de exito, junto a redireccion a stock
        flash.addFlashAttribute("success", "Stock eliminado exitosamente");
        return "redirect:/stock";
    }


    /**
     * Permite editar el stock del StockProducto
     * @param tiendaId Identificador de la tienda
     * @param productoId Identificador del producto
     * @param stock Stock a cambiar
     * @param model Modelo
     * @param flash Atributos de redireccion
     * @return Redireccion
     */
    @GetMapping("/editar")
    public String editar(@RequestParam Long tiendaId,
                         @RequestParam Long productoId,
                         @RequestParam Long stock,
                         Model model,
                         RedirectAttributes flash)
    {
        // Comprobamos si los parametros no son nulos, si no lo son mandamos a stock con mensaje de error
        if (tiendaId == null || productoId == null || stock == null)
        {
            flash.addFlashAttribute("error", "Los parametros insertados estan vacios");
            return "redirect:/stock";
        }

        // Comprobamos que el id de la tienda es igual que el id pasado, si no lo son mandamos a stock con mensaje de error
        if (!Objects.equals(tienda(model).getId(), tiendaId))
        {
            flash.addFlashAttribute("error", "No pertenece a la tienda correspondiente");
            return "redirect:/stock";
        }

        // Creamos el id de stock producto con los campos requeridos
        StockProductoId stockProductoId = new StockProductoId();

        stockProductoId.setProductoId(productoId);
        stockProductoId.setTiendaId(tiendaId);

        // Creamos el stockProducto a insertar en la base de datos
        StockProducto stockProducto = new StockProducto();

        stockProducto.setId(stockProductoId);
        stockProducto.setStock(stock);

        stockService.save(stockProducto);

        // Mandamos mensaje de exito, junto a redireccion a stock
        flash.addFlashAttribute("success", "Stock editado exitosamente");
        return "redirect:/stock";
    }
}
