package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

@SessionAttributes("cliente")
public abstract class AbsController
{
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @ModelAttribute
    public Cliente cliente(Model model)
    {
        return (Cliente) model.getAttribute("cliente");
    }

    /**
     * Obtiene el path de origen si viene de la aplicacion, sino redirigira al path root (/)
     * @param request Request
     * @return Path de redireccion
     */
    protected String pathOrigenRedirect(HttpServletRequest request)
    {
        String referer = request.getHeader("Referer");

        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }

        return "redirect:/";
    }
}
