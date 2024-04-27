package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IEmpleadoCrud;
import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmpleadoServiceImpl implements IEmpleadoService
{
    @Autowired
    private IEmpleadoCrud empleadoCrud;

    @Override
    @Transactional(readOnly = true)
    public List<Empleado> findByTienda(Tienda tienda) {
        return empleadoCrud.findByTienda(tienda);
    }

    @Override
    @Transactional(readOnly = true)
    public Empleado findById(Long id) {
        return empleadoCrud.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void save(Empleado empleado) {
        empleadoCrud.save(empleado);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        empleadoCrud.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validarCorreo(String correo)
    {
        return empleadoCrud.existsByCuenta_Correo(correo);
    }
}
