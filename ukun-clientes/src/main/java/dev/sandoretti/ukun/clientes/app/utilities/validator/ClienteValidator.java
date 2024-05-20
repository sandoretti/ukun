package dev.sandoretti.ukun.clientes.app.utilities.validator;

import dev.sandoretti.ukun.clientes.app.models.dao.IClienteDAO;
import dev.sandoretti.ukun.clientes.app.models.entity.Cliente;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ClienteValidator implements Validator
{
    @Autowired
    private IClienteDAO clienteDAO;

    @Override
    public boolean supports(Class<?> clazz)
    {
        return Cliente.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors)
    {
        Cliente cliente = (Cliente) target;

        if (clienteDAO.existsByCuenta_Correo(cliente.getCuenta().getCorreo().toLowerCase()))
            errors.rejectValue("cuenta.correo", "Correo.Existe");
    }
}
