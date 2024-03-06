-- Tabla cuenta
-- La tabla cuenta almacena la información de las cuentas de los clientes y empleados
CREATE TABLE cuenta (
    id SERIAL PRIMARY KEY,                  -- Identificador de la cuenta
    correo VARCHAR(255) UNIQUE,             -- Correo electrónico de la cuenta
    contrasenna VARCHAR(255) NOT NULL,      -- Contraseña de la cuenta
    nombre VARCHAR(255) NOT NULL,           -- Nombre del propietario de la cuenta
    apellido VARCHAR(255) NOT NULL          -- Apellido del propietario de la cuenta
);

-- Tabla direccion
-- La tabla dirección almacena la información de las direcciones de los clientes y tiendas
CREATE TABLE direccion (
    id SERIAL PRIMARY KEY,              -- Identificador de la dirección
    calle VARCHAR(255) NOT NULL,        -- Calle de la dirección
    provincia VARCHAR(255) NOT NULL,    -- Provincia de la dirección
    ciudad VARCHAR(255) NOT NULL,       -- Ciudad de la dirección
    codigo_postal VARCHAR(10) NOT NULL  -- Código postal de la dirección
);

-- Tabla tarjeta
-- La tabla tarjeta almacena la información de las tarjetas de los clientes
CREATE TABLE tarjeta (
    id SERIAL PRIMARY KEY,          -- Identificador de la tarjeta
    numero VARCHAR(16) NOT NULL,    -- Número de la tarjeta
    fecha_expiracion DATE NOT NULL, -- Fecha de expiración de la tarjeta
    cvv VARCHAR(3) NOT NULL         -- Código de seguridad de la tarjeta
);

-- Tabla tipo_producto
-- La tabla tipo_producto almacena la información de los tipos de productos
CREATE TABLE tipo_producto (
    id SERIAL PRIMARY KEY,          -- Identificador del tipo de producto
    nombre VARCHAR(255) NOT NULL    -- Nombre del tipo de producto
);

-- Tabla producto
-- La tabla producto almacena la información de los productos
CREATE TABLE producto (
    id SERIAL PRIMARY KEY,          -- Identificador del producto
    nombre VARCHAR(255) NOT NULL,   -- Nombre del producto
    precio DECIMAL(10, 2) NOT NULL, -- Precio del producto
    tipo INTEGER NOT NULL,          -- Identificador del tipo de producto
    dir_foto VARCHAR(255),          -- Dirección de la foto del producto
    descripcion TEXT NOT NULL,      -- Descripción del producto

    -- Foreign key de la tabla tipo_producto
    FOREIGN KEY (tipo) REFERENCES tipo_producto(id)
);

-- Tabla tienda
-- La tabla tienda almacena la información de las tiendas
CREATE TABLE tienda (
    id SERIAL PRIMARY KEY,          -- Identificador de la tienda
    nombre VARCHAR(255) NOT NULL,   -- Nombre de la tienda
    direccion_id INTEGER NOT NULL,  -- Identificador de la dirección de la tienda
    telefono VARCHAR(9) NOT NULL,   -- Teléfono de la tienda

    -- Foreign key de la tabla direccion 
    FOREIGN KEY (direccion_id) REFERENCES direccion(id)
);

-- Tabla cliente
-- La tabla cliente almacena la información de los clientes
CREATE TABLE cliente (
    id INTEGER REFERENCES cuenta(id),   -- Identificador del cliente
    id_direccion INTEGER,               -- Identificador de la dirección del cliente
    id_tarjeta INTEGER,                 -- Identificador de la tarjeta del cliente
    tienda_fav INTEGER,                 -- Identificador de la tienda favorita del cliente

    -- Foreign keys de las tablas direccion, tarjeta y tienda
    FOREIGN KEY (id_direccion) REFERENCES direccion(id),
    FOREIGN KEY (id_tarjeta) REFERENCES tarjeta(id),
    FOREIGN KEY (tienda_fav) REFERENCES tienda(id),

    -- Primary key del cliente
    PRIMARY KEY (id)
);

-- Tabla empleado
-- La tabla empleado almacena la información de los empleados de las tiendas
CREATE TABLE empleado (
    id INTEGER REFERENCES cuenta(id),       -- Identificador del empleado
    admin BOOLEAN NOT NULL DEFAULT FALSE,   -- Si el empleado es administrador o no
    empleo VARCHAR(255) NOT NULL,           -- Empleo del empleado
    tienda_id INTEGER NOT NULL,             -- Identificador de la tienda del empleado

    -- Foreign key de la tabla tienda
    FOREIGN KEY (tienda_id) REFERENCES tienda(id),

    -- Primary key del empleado
    PRIMARY KEY (id)
);

-- Tabla stock_tienda
-- La tabla stock_tienda almacena la información del stock de los productos en las tiendas
CREATE TABLE stock_tienda (
    producto_id INTEGER,   -- Identificador del producto
    tienda_id INTEGER,     -- Identificador de la tienda
    stock INTEGER,         -- Stock del producto en la tienda

    -- Foreign keys de las tablas producto y tienda
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (tienda_id) REFERENCES tienda(id),

    -- Primary key compuesta por producto_id y tienda_id
    PRIMARY KEY (producto_id, tienda_id)
);

-- Tabla carrito
-- La tabla carrito almacena la información de los productos en el carrito de los clientes
CREATE TABLE carrito (
    cliente_id INTEGER,         -- Identificador del cliente
    producto_id INTEGER,        -- Identificador del producto
    cantidad INTEGER NOT NULL,  -- Cantidad del producto en el carrito

    -- Foreign keys de las tablas cliente y producto
    FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),

    -- Primary key compuesta por cliente_id y producto_id
    PRIMARY KEY (cliente_id, producto_id)
);

-- Tabla pedido
-- La tabla pedido almacena la información de los pedidos de los clientes
CREATE TABLE pedido (
    id SERIAL PRIMARY KEY,          -- Identificador del pedido
    cliente_id INTEGER NOT NULL,    -- Identificador del cliente
    fecha DATE NOT NULL,            -- Fecha del pedido
    total DECIMAL(10, 2) NOT NULL,  -- Total del pedido
    direccion_id INTEGER NOT NULL,  -- Identificador de la dirección del pedido
    tarjeta_id INTEGER NOT NULL,    -- Identificador de la tarjeta del pedido

    -- Foreign keys de las tablas cliente, direccion y tarjeta
    FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    FOREIGN KEY (direccion_id) REFERENCES direccion(id),
    FOREIGN KEY (tarjeta_id) REFERENCES tarjeta(id)
);

-- Tabla producto_pedido
-- La tabla producto_pedido almacena la información de los productos en los pedidos de los clientes
CREATE TABLE producto_pedido (
    pedido_id INTEGER NOT NULL,     -- Identificador del pedido
    producto_id INTEGER NOT NULL,   -- Identificador del producto
    cantidad INTEGER NOT NULL,      -- Cantidad del producto en el pedido

    -- Foreign keys de las tablas pedido y producto
    FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),

    -- Primary key compuesta por pedido_id y producto_id
    PRIMARY KEY (pedido_id, producto_id)
);

