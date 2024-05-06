package dev.sandoretti.ukun.clientes.app.services;

import dev.sandoretti.ukun.clientes.app.models.dao.IStockProductoDAO;
import dev.sandoretti.ukun.clientes.app.models.entity.StockProducto;
import dev.sandoretti.ukun.clientes.app.models.entity.Tienda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockServiceImpl implements IStockService
{
    @Autowired
    private IStockProductoDAO stockDAO;

    @Override
    @Transactional(readOnly = true)
    public List<StockProducto> findByTienda(Tienda tienda) {
        return stockDAO.findStockProductoByTienda(tienda);
    }
}
