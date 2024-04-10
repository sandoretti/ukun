package dev.sandoretti.ukun.empleados.app.models.service;

import dev.sandoretti.ukun.empleados.app.models.dao.IEmpleadoCrud;
import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public List<Empleado> findAll() {
        return (List<Empleado>) empleadoCrud.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Empleado> findAll(Pageable pageable) {
        return empleadoCrud.findAll(pageable);
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
}
