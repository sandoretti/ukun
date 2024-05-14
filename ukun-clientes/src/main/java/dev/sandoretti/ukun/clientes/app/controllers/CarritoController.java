package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.embeddable.CarritoId;
import dev.sandoretti.ukun.clientes.app.models.entity.Carrito;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Producto;
import dev.sandoretti.ukun.clientes.app.services.ICarritoService;
import dev.sandoretti.ukun.clientes.app.services.IProductoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/carrito")
public class CarritoController extends AbsController
{
    @Autowired
    private ICarritoService carritoService;

    @Autowired
    private IProductoService productoService;

    @GetMapping({"", "/"})
    public String index(@ModelAttribute("cliente") Cliente cliente,
                        Model model)
    {
        List<Carrito> carritoProductos = carritoService.findByCliente(cliente);
        float total = 0f;

        for (Carrito carrito : carritoProductos)
        {
            total += carrito.getCantidad() * carrito.getProducto().getPrecio();
        }

        model.addAttribute("carritoProductos", carritoProductos);
        model.addAttribute("total", total);

        return "carrito";
    }

    @GetMapping("/add")
    public String add(@RequestParam(name = "producto") Long idProducto,
                      @RequestParam(name = "cantidad", defaultValue = "1") Long cantidad,
                      @ModelAttribute("cliente") Cliente cliente,
                      RedirectAttributes flash,
                      HttpServletRequest request)
    {
        // Obtenemos el path de origen si viene correctamente
        String pathOrigen = pathOrigenRedirect(request);

        // Obtenemos el producto de la base de datos
        Producto producto = productoService.findById(idProducto);

        // Si el carrito se ha encontrado o si la cantidad es menor a 1, devolvemos un error
        if (producto == null || cantidad < 1)
        {
            flash.addFlashAttribute("error", "Error al añadir el producto en el carrito");
            return pathOrigen;
        }

        Long idCliente = cliente.getId();   // Obtenemos el id del cliente
        CarritoId carritoId = new CarritoId(idCliente, idProducto); // Construimos el id del carrito

        // Construimos el carrito con los datos obtenidos
        Carrito carrito = new Carrito(carritoId, cliente, producto, cantidad);

        // Guardamos el carrito en la base de datos
        carritoService.guardar(carrito);

        flash.addFlashAttribute("info", "Producto añadido al carrito");

        return pathOrigen;
    }

    @GetMapping("/delete")
    public String delete(@RequestParam(name = "producto") Long idProducto,
                         @ModelAttribute("cliente") Cliente cliente,
                         RedirectAttributes flash,
                         HttpServletRequest request)
    {
        // Construimos el id del carrito
        CarritoId carritoId = new CarritoId(cliente.getId(), idProducto);

        // Eliminamos el producto del carrito del cliente, si no existe lo ignora
        carritoService.eliminar(carritoId);

        // Mandamos un mensaje de exito
        flash.addFlashAttribute("info", "Producto eliminado al carrito");

        // Volvemos a la pagina de origen
        return pathOrigenRedirect(request);
    }
}
