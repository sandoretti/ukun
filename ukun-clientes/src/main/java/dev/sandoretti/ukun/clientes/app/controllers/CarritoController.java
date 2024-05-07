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
        model.addAttribute("carritoProductos", carritoProductos);

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

        // Construimos el carrito con los atributos necesarios
        Carrito carrito = new Carrito();    // Inicializamos carrito
        Long idCliente = cliente.getId();   // Obtenemos el id del cliente
        CarritoId carritoId = new CarritoId(idCliente, idProducto); // Construimos el id del carrito
        carrito.setId(carritoId);   // Establecemos el id del carrito
        carrito.setCantidad(cantidad);  // Establecemos la cantidad a añadir
        carrito.setCliente(cliente);    // Establecemos el cliente
        carrito.setProducto(producto);  // Establecemos el producto

        // Guardamos el carrito en la base de datos
        carritoService.guardar(carrito);

        flash.addFlashAttribute("info", "Producto annadido al carrito");

        return pathOrigen;
    }
}
