package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IEmpleadoCrud;
import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginService implements ILoginService
{
    @Autowired
    private IEmpleadoCrud empleadoCrud;

    @Override
    public Empleado loginEmpleado(String correo, String contrasenna) {
        return empleadoCrud.findByCorreoAndContrasenna(correo, contrasenna);
    }
}
