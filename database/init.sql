-- Tabla cuenta
-- La tabla cuenta almacena la información de las cuentas de los clientes y empleados
CREATE TABLE cuenta (
    id SERIAL PRIMARY KEY,                  -- Identificador de la cuenta
    correo VARCHAR(255) UNIQUE,             -- Correo electrónico de la cuenta
    contrasenna VARCHAR(16) NOT NULL,       -- Contraseña de la cuenta
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

--DATOS INICIALES
-- Tabla tipo_producto
INSERT INTO tipo_producto (nombre) VALUES ('Mesas');
INSERT INTO tipo_producto (nombre) VALUES ('Sillas');
INSERT INTO tipo_producto (nombre) VALUES ('Camas');
INSERT INTO tipo_producto (nombre) VALUES ('Escritorios');
INSERT INTO tipo_producto (nombre) VALUES ('Sofás');

-- Tabla producto
INSERT INTO producto (nombre, precio, tipo, dir_foto, descripcion) VALUES
(
    'Mesa de madera',
    50.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Mesas'),
    'mesa-madera.png',
    'Mesa de madera de color marrón'
),
(
    'Silla de plástico',
    10.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Sillas'),
    'silla-plastico.png',
    'Silla de plástico de color blanco'
),
(
    'Cama individual',
    80.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Camas'),
    'cama-individual.png',
    'Cama individual de color azul'
),
(
    'Escritorio de cristal',
    60.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Escritorios'),
    'escritorio-cristal.png',
    'Escritorio de cristal de color transparente'
),
(
    'Sofá de cuero',
    120.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Sofás'),
    'sofa-cuero.png',
    'Sofá de cuero muy cómodo'
),
(
    'Cama de matrimonio',
    100.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Camas'),
    'cama-matrimonio.png',
    'Cama de matrimonio tamaño king size'
),
(
    'Escritorio de madera',
    70.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Escritorios'),
    'escritorio-madera.png',
    'Escritorio de madera perfecto para trabajar'
),
(
    'Sofá de tela',
    150.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Sofás'),
    'sofa-tela.png',
    'Sofá de tela de color gris'
),
(
    'Mesa de cristal',
    40.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Mesas'),
    'mesa-cristal.png',
    'Mesa de cristal de color transparente'
),
(
    'Silla de madera',
    20.00,
    (SELECT id FROM tipo_producto WHERE nombre = 'Sillas'),
    'silla-madera.png',
    'Silla de madera de color marrón'
);

-- Tabla tienda
WITH direccion_data AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) VALUES ('Calle Mayor, 1', 'Madrid', 'Madrid', '28001') RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) VALUES
(
    'Ukun Online',
    (SELECT id FROM direccion_data),
    '912345670'
);

WITH direccion_data AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) VALUES ('Vía Complutense, 1', 'Madrid', 'Alcalá de Henares', '28801') RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) VALUES
(
    'Ukun Alcalá de Henares',
    (SELECT id FROM direccion_data),
    '912345678'
);

WITH direccion_data AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) VALUES ('Avenida de la Constitución, 12', 'Madrid', 'Torrejón de Ardoz', '28103') RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) VALUES
(
    'Ukun Torrejón de Ardoz',
    (SELECT id FROM direccion_data),
    '912345679'
);

WITH direccion_data AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) VALUES ('Avenida Diagonal, 4', 'Barcelona', 'Barcelona', '56123') RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) VALUES
(
    'Ukun Barcelona',
    (SELECT id FROM direccion_data),
    '934567890'
);

-- Tabla empleado
WITH empleado_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('salvador.oton@ukun.com', '123456', 'Salvador', 'Oton') RETURNING id
)
INSERT INTO empleado (id, admin, empleo, tienda_id) VALUES
(
    (SELECT id FROM empleado_data),
    TRUE,
    'Gerente',
    (SELECT id FROM tienda WHERE nombre = 'Ukun Online')
);

WITH empleado_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('joel.sandoval@ukun.com', '123456', 'Joel', 'Sandoval') RETURNING id
)
INSERT INTO empleado (id, admin, empleo, tienda_id) VALUES
(
    (SELECT id FROM empleado_data),
    TRUE,
    'Gerente',
    (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares')
);

WITH empleado_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('daniel.garcia@ukun.com', '123456', 'Daniel', 'García') RETURNING id
)
INSERT INTO empleado (id, admin, empleo, tienda_id) VALUES
(
    (SELECT id FROM empleado_data),
    FALSE,
    'Vendedor',
    (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares')
);

WITH empleado_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('miguel.virtus@ukun.com', '123456', 'Miguel', 'Virtus') RETURNING id
)
INSERT INTO empleado (id, admin, empleo, tienda_id) VALUES
(
    (SELECT id FROM empleado_data),
    TRUE,
    'Gerente',
    (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz')
);

WITH empleado_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('alvaro.mazo@ukun.com', '123456', 'Álvaro', 'Mazo') RETURNING id
)
INSERT INTO empleado (id, admin, empleo, tienda_id) VALUES
(
    (SELECT id FROM empleado_data),
    FALSE,
    'Vendedor',
    (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz')
);

WITH empleado_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('javier.castillo@ukun.com', '123456', 'Javier', 'Castillo') RETURNING id
)
INSERT INTO empleado (id, admin, empleo, tienda_id) VALUES
(
    (SELECT id FROM empleado_data),
    TRUE,
    'Gerente',
    (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona')
);

WITH empleado_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('marcos.rincon@ukun.com', '123456', 'Marcos', 'Rincón') RETURNING id
)
INSERT INTO empleado (id, admin, empleo, tienda_id) VALUES
(
    (SELECT id FROM empleado_data),
    FALSE,
    'Vendedor',
    (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona')
);

-- Tabla stock_tienda
WITH tienda_alcala as (
    SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'
)
INSERT INTO stock_tienda (producto_id, tienda_id, stock) VALUES
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de madera'),
    (SELECT id FROM tienda_alcala),
    10
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de plástico'),
    (SELECT id FROM tienda_alcala),
    20
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama individual'),
    (SELECT id FROM tienda_alcala),
    15
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'),
    (SELECT id FROM tienda_alcala),
    5
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de cuero'),
    (SELECT id FROM tienda_alcala),
    8
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'),
    (SELECT id FROM tienda_alcala),
    12
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de madera'),
    (SELECT id FROM tienda_alcala),
    7
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de tela'),
    (SELECT id FROM tienda_alcala),
    10
),
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de cristal'),
    (SELECT id FROM tienda_alcala),
    15
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de madera'),
    (SELECT id FROM tienda_alcala),
    25
);

WITH tienda_torrejon as (
    SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'
)
INSERT INTO stock_tienda (producto_id, tienda_id, stock) VALUES
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de madera'),
    (SELECT id FROM tienda_torrejon),
    5
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de plástico'),
    (SELECT id FROM tienda_torrejon),
    15
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama individual'),
    (SELECT id FROM tienda_torrejon),
    10
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'),
    (SELECT id FROM tienda_torrejon),
    3
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de cuero'),
    (SELECT id FROM tienda_torrejon),
    6
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'),
    (SELECT id FROM tienda_torrejon),
    8
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de madera'),
    (SELECT id FROM tienda_torrejon),
    4
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de tela'),
    (SELECT id FROM tienda_torrejon),
    7
),
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de cristal'),
    (SELECT id FROM tienda_torrejon),
    10
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de madera'),
    (SELECT id FROM tienda_torrejon),
    20
);

WITH tienda_barcelona as (
    SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'
)
INSERT INTO stock_tienda (producto_id, tienda_id, stock) VALUES
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de madera'),
    (SELECT id FROM tienda_barcelona),
    8
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de plástico'),
    (SELECT id FROM tienda_barcelona),
    18
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama individual'),
    (SELECT id FROM tienda_barcelona),
    13
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'),
    (SELECT id FROM tienda_barcelona),
    4
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de cuero'),
    (SELECT id FROM tienda_barcelona),
    7
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'),
    (SELECT id FROM tienda_barcelona),
    10
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de madera'),
    (SELECT id FROM tienda_barcelona),
    6
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de tela'),
    (SELECT id FROM tienda_barcelona),
    9
),
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de cristal'),
    (SELECT id FROM tienda_barcelona),
    12
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de madera'),
    (SELECT id FROM tienda_barcelona),
    22
);

WITH tienda_online as (
    SELECT id FROM tienda WHERE nombre = 'Ukun Online'
)
INSERT INTO stock_tienda (producto_id, tienda_id, stock) VALUES
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de madera'),
    (SELECT id FROM tienda_online),
    20
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de plástico'),
    (SELECT id FROM tienda_online),
    30
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama individual'),
    (SELECT id FROM tienda_online),
    25
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'),
    (SELECT id FROM tienda_online),
    10
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de cuero'),
    (SELECT id FROM tienda_online),
    15
),
(
    (SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'),
    (SELECT id FROM tienda_online),
    20
),
(
    (SELECT id FROM producto WHERE nombre = 'Escritorio de madera'),
    (SELECT id FROM tienda_online),
    12
),
(
    (SELECT id FROM producto WHERE nombre = 'Sofá de tela'),
    (SELECT id FROM tienda_online),
    18
),
(
    (SELECT id FROM producto WHERE nombre = 'Mesa de cristal'),
    (SELECT id FROM tienda_online),
    25
),
(
    (SELECT id FROM producto WHERE nombre = 'Silla de madera'),
    (SELECT id FROM tienda_online),
    35
);


-- Tabla cliente
WITH cliente_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('joel.sandoval@email.com', '123456', 'Joel', 'Sandoval') RETURNING id
),
direccion_data AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal)
    VALUES ('Calle Mayor, 1', 'Madrid', 'Alcalá de Henares', '28803') RETURNING id
),
tarjeta_data AS (
    INSERT INTO tarjeta (numero, fecha_expiracion, cvv)
    VALUES ('1234567890123456', '2025-12-01', '123') RETURNING id
)
INSERT INTO cliente (id, id_direccion, id_tarjeta, tienda_fav)
VALUES
(
    (SELECT id FROM cliente_data),
    (SELECT id FROM direccion_data),
    (SELECT id FROM tarjeta_data),
    (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares')
);

WITH cliente_data AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES ('carmen.amoretti@email.com', '123456', 'Carmen', 'Amoretti') RETURNING id
),
direccion_data AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal)
    VALUES ('Avenida de la Constitución, 1', 'Madrid', 'Torrejón de Ardoz', '28103') RETURNING id
)
INSERT INTO cliente (id, id_direccion, tienda_fav)
VALUES
(
    (SELECT id FROM cliente_data),
    (SELECT id FROM direccion_data),
    (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz')
);