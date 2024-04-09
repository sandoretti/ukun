package dev.sandoretti.ukun.empleados.app.models.dao;

import dev.sandoretti.ukun.empleados.app.models.entity.Empleado;
import dev.sandoretti.ukun.empleados.app.models.entity.Tienda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("empleadoDao")
public class EmpleadoDaoImpl implements IEmpleadoDao
{
    @PersistenceContext
    private EntityManager em;


    @Override
    public List<Empleado> findAll() {
        return em.createQuery("from Empleado", Empleado.class).getResultList();
    }

    @Override
    public Empleado findById(Long id) {
        return null;
    }

    @Override
    public void save(Empleado empleado) {
        // Si el empleado se encuentra en la base de datos lo actualizamos
        if (empleado.getId() != null && empleado.getId()>0)
        {
            em.merge(empleado);
        }
        // Si no lo insertamos dentro de la base de datos
        else
        {
            em.persist(empleado);
        }
    }

    @Override
    public void delete(Long id) {
        // Buscamos el empleado en la base de datos
        Empleado empleado = em.find(Empleado.class, id);

        // Si el empleado existe, lo eliminamos
        if (empleado != null) em.remove(empleado);
    }

    @Override
    public List<Empleado> findEmpleadosTienda(Tienda tienda) {
        return findAll();
        //return em.createQuery("FROM Empleado WHERE tienda_id like :idTienda", Empleado.class).setParameter("idTienda", tienda.getId()).getResultList();
    }

}
