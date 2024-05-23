package dev.sandoretti.ukun.empleados.app.controllers;

import dev.sandoretti.ukun.empleados.app.models.embeddable.StockProductoId;
import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import dev.sandoretti.ukun.empleados.app.service.IStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
     * @param empleado Empleado
     * @return Tienda del empleado
     */
    @ModelAttribute("tienda")
    public Tienda tienda(@ModelAttribute("empleado") Empleado empleado)
    {
        return empleado.getTienda();
    }

    @GetMapping(value = {"", "/"})
    public String menu(@ModelAttribute("tienda") Tienda tienda,
                       Model model)
    {
        model.addAttribute("stockProductosList", stockService.findStockProductoByTienda(tienda));
        model.addAttribute("productoNoTienda", stockService.findProductoNotInTienda(tienda));

        return "stock";
    }

    /**
     * Eliminar el stockProducto de la base de datos
     * @param tiendaId Identificador de la tienda
     * @param productoId Identificador del producto
     * @param tienda Tienda del empleado
     * @param flash Atributos de redireccion
     * @return Redireccion
     */
    @GetMapping("/eliminar")
    public String eliminar(@RequestParam Long tiendaId,
                           @RequestParam Long productoId,
                           @ModelAttribute("tienda") Tienda tienda,
                           RedirectAttributes flash)
    {
        // Comprobamos si los parametros no son nulos, si no lo son mandamos a stock con mensaje de error
        if (tiendaId == null || productoId == null)
        {
            flash.addFlashAttribute("error", "Los parametros insertados estan vacios");
            return "redirect:/stock";
        }

        // Comprobamos que el id de la tienda es igual que el id pasado, si no lo son mandamos a stock con mensaje de error
        if (!Objects.equals(tienda.getId(), tiendaId))
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
     * @param tienda Tienda del cliente
     * @param flash Atributos de redireccion
     * @return Redireccion
     */
    @GetMapping("/editar")
    public String editar(@RequestParam Long tiendaId,
                         @RequestParam Long productoId,
                         @RequestParam Long stock,
                         @ModelAttribute("tienda") Tienda tienda,
                         RedirectAttributes flash)
    {
        // Comprobamos si los parametros no son nulos, si no lo son mandamos a stock con mensaje de error
        if (tiendaId == null || productoId == null || stock == null)
        {
            flash.addFlashAttribute("error", "Los parametros insertados estan vacios");
            return "redirect:/stock";
        }

        // Comprobamos que el id de la tienda es igual que el id pasado, si no lo son mandamos a stock con mensaje de error
        if (!Objects.equals(tienda.getId(), tiendaId))
        {
            flash.addFlashAttribute("error", "No pertenece a la tienda correspondiente");
            return "redirect:/stock";
        }

        if (stock < 0)
        {
            flash.addFlashAttribute("error", "El stock es negativo");
            return "redirect:/stock";
        }

        if (!stockService.guardar(tienda, productoId, stock))
            flash.addFlashAttribute("error", "Error al guardar el stock");
        else
            flash.addFlashAttribute("success", "Stock guardado exitosamente");

        return "redirect:/stock";
    }
}
