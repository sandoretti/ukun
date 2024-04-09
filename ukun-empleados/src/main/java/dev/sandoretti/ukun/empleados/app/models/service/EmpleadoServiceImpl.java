package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IEmpleadoCrud;
import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmpleadoServiceImpl implements IEmpleadoService
{
    @Autowired
    private IEmpleadoCrud empleadoCrud;

    @Override
    public List<Empleado> findAll() {
        return (List<Empleado>) empleadoCrud.findAll();
    }

    @Override
    public Page<Empleado> findAll(Pageable pageable) {
        return empleadoCrud.findAll(pageable);
    }

    @Override
    public Empleado findById(Long id) {
        return empleadoCrud.findById(id).orElse(null);
    }

    @Override
    public void save(Empleado empleado) {
        empleadoCrud.save(empleado);
    }

    @Override
    public void delete(Long id) {
        empleadoCrud.deleteById(id);
    }
}
