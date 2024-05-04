package dev.sandoretti.ukun.clientes.app.controllers;

import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
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
}
