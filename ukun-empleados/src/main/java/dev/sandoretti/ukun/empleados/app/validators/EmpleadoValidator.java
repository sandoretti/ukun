package dev.sandoretti.ukun.empleados.app.validators;

import dev.sandoretti.ukun.empleados.app.models.dao.IEmpleadoCrud;
import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class EmpleadoValidator implements Validator
{
    @Autowired
    private IEmpleadoCrud empleadoCrud;

    @Override
    public boolean supports(Class<?> clazz)
    {
        return Empleado.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors)
    {
        Empleado empleado = (Empleado) target;

        if (empleadoCrud.existsByCuenta_Correo(empleado.getCuenta().getCorreo()))
            errors.rejectValue("cuenta.correo", "Correo.Existe");
    }
}
