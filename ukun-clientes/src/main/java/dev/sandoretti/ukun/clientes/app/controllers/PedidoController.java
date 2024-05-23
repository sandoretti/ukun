package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import dev.sandoretti.ukun.clientes.app.models.entity.Pedido;
import dev.sandoretti.ukun.clientes.app.services.IPedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/pedidos")
public class PedidoController extends AbsController
{
    @Autowired
    private IPedidoService pedidoService;

    @GetMapping({"", "/"})
    public String index(@ModelAttribute("cliente") Cliente cliente,
                        Model model)
    {
        List<Pedido> pedidos = pedidoService.obtenerPorCliente(cliente);

        model.addAttribute("pedidos", pedidos);

        return "pedidos";
    }

    @GetMapping("/hacer")
    public String realizarPedido(@ModelAttribute("cliente") Cliente cliente,
                                 RedirectAttributes flash)
    {
        Pedido pedido = pedidoService.guardar(cliente);

        if (pedido == null)
        {
            flash.addFlashAttribute("error", "Hubo un error al realizar el pedido");

            return "redirect:/pedidos";
        }

        flash.addFlashAttribute("success", "Se ha creado el pedido correctamente");

        return "redirect:/pedidos";
    }
}
