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
    fecha TIMESTAMP NOT NULL,            -- Fecha del pedido
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
    pedido_id INTEGER NOT NULL,                 -- Identificador del pedido
    producto_id INTEGER NOT NULL,               -- Identificador del producto
    cantidad INTEGER NOT NULL,                  -- Cantidad del producto en el pedido
    precio_unitario DECIMAL(10, 2) NOT NULL,    -- Precio unitario del producto
    precio_total DECIMAL(10, 2) NOT NULL,       -- Precio total del producto

    -- Foreign keys de las tablas pedido y producto
    FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),

    -- Primary key compuesta por pedido_id y producto_id
    PRIMARY KEY (pedido_id, producto_id)
);

--DATOS INICIALES
-- Insertar datos en la tabla tipo_producto
INSERT INTO tipo_producto (nombre) VALUES 
('Mesas'),
('Sillas'),
('Camas'),
('Escritorios'),
('Sofás'),
('Decoración');

-- Insertar datos en la tabla producto
INSERT INTO producto (nombre, precio, tipo, dir_foto, descripcion) VALUES
-- Mesas
('Mesa de madera', 149.05, (SELECT id FROM tipo_producto WHERE nombre = 'Mesas'), 'mesa-madera.jpg', 'Elegante mesa de madera de color marrón, perfecta para cualquier comedor o cocina. Esta mesa está diseñada con un acabado liso que realza la belleza natural de la madera, proporcionando una superficie duradera y fácil de limpiar.'),
('Mesa de cristal', 129.75, (SELECT id FROM tipo_producto WHERE nombre = 'Mesas'), 'mesa-cristal.jpg', 'Mesa de cristal de color transparente, perfecta para comedores y salas de estar. Su diseño minimalista y su estructura robusta la hacen ideal para espacios modernos, ofreciendo una superficie resistente y fácil de limpiar.'),
('Mesa de comedor extendible', 219.50, (SELECT id FROM tipo_producto WHERE nombre = 'Mesas'), 'mesa-comedor-extendible.jpg', 'Mesa de comedor extendible, ideal para reuniones familiares y cenas con amigos. Fabricada con madera de alta calidad y un diseño elegante, esta mesa se adapta a tus necesidades de espacio.'),
('Mesa auxiliar de metal', 79.25, (SELECT id FROM tipo_producto WHERE nombre = 'Mesas'), 'mesa-auxiliar-metal.jpg', 'Mesa auxiliar de metal con un diseño industrial y minimalista. Perfecta para el salón o como mesita de noche, esta mesa ofrece funcionalidad y estilo en un solo producto.'),

-- Sillas
('Silla de plástico', 24.99, (SELECT id FROM tipo_producto WHERE nombre = 'Sillas'), 'silla-plastico.jpg', 'Silla de plástico de color blanco, ideal para uso interior y exterior. Su diseño ergonómico y ligero la hace fácil de transportar y almacenar, ofreciendo comodidad y resistencia a un precio asequible.'),
('Silla de madera', 59.50, (SELECT id FROM tipo_producto WHERE nombre = 'Sillas'), 'silla-madera.jpg', 'Silla de madera de color marrón, con un diseño clásico y elegante. Ideal para comedores y oficinas, esta silla combina comodidad y estilo con una estructura duradera y fácil de mantener.'),
('Silla ergonómica de oficina', 119.99, (SELECT id FROM tipo_producto WHERE nombre = 'Sillas'), 'silla-ergonomica.jpg', 'Silla ergonómica de oficina, diseñada para ofrecer el máximo confort durante largas jornadas de trabajo. Su respaldo ajustable y asiento acolchado garantizan una postura saludable y cómoda.'),
('Silla de comedor tapizada', 89.25, (SELECT id FROM tipo_producto WHERE nombre = 'Sillas'), 'silla-comedor-tapizada.jpg', 'Silla de comedor tapizada en tela gris, con un diseño moderno y elegante. Ideal para comedores y salones, esta silla ofrece comodidad y estilo, complementando perfectamente cualquier mesa de comedor.'),

-- Camas
('Cama individual', 249.99, (SELECT id FROM tipo_producto WHERE nombre = 'Camas'), 'cama-individual.jpg', 'Cama individual de color azul con un diseño moderno y minimalista. Fabricada con materiales de alta calidad para garantizar un buen descanso. Su estructura robusta y fácil de montar es perfecta para cualquier habitación.'),
('Cama de matrimonio', 349.15, (SELECT id FROM tipo_producto WHERE nombre = 'Camas'), 'cama-matrimonio.jpg', 'Cama de matrimonio tamaño king size con un diseño elegante y minimalista. La estructura de la cama es robusta y duradera, proporcionando un soporte óptimo para un descanso placentero. Su diseño moderno encaja perfectamente en cualquier dormitorio.'),
('Cama de día', 299.50, (SELECT id FROM tipo_producto WHERE nombre = 'Camas'), 'cama-dia.jpg', 'Cama de día multifuncional, ideal para salas de estar y habitaciones de invitados. Su diseño versátil permite utilizarla como sofá durante el día y como cama cómoda por la noche.'),
('Litera infantil', 399.99, (SELECT id FROM tipo_producto WHERE nombre = 'Camas'), 'litera-infantil.jpg', 'Litera infantil de diseño divertido y seguro, perfecta para habitaciones compartidas. Fabricada con materiales resistentes, esta litera maximiza el espacio y ofrece una solución práctica y atractiva para los niños.'),

-- Escritorios
('Escritorio de cristal', 179.75, (SELECT id FROM tipo_producto WHERE nombre = 'Escritorios'), 'escritorio-cristal.jpg', 'Escritorio de cristal transparente con un diseño elegante y contemporáneo. Ideal para oficinas en casa y espacios de trabajo. Su superficie de cristal templado es resistente y fácil de limpiar, aportando un toque sofisticado a tu entorno.'),
('Escritorio de madera', 219.35, (SELECT id FROM tipo_producto WHERE nombre = 'Escritorios'), 'escritorio-madera.jpg', 'Escritorio de madera perfecto para trabajar, con un amplio espacio de superficie y un diseño minimalista que se adapta a cualquier estilo de decoración. La madera de alta calidad asegura una larga durabilidad y un acabado elegante.'),
('Escritorio compacto', 149.99, (SELECT id FROM tipo_producto WHERE nombre = 'Escritorios'), 'escritorio-compacto.jpg', 'Escritorio compacto ideal para espacios pequeños. Su diseño funcional incluye almacenamiento integrado y una superficie de trabajo cómoda, perfecto para estudiantes y oficinas en casa.'),
('Escritorio con estantería', 249.50, (SELECT id FROM tipo_producto WHERE nombre = 'Escritorios'), 'escritorio-estanteria.jpg', 'Escritorio con estantería incorporada, combinando almacenamiento y espacio de trabajo en un solo mueble. Su diseño moderno y práctico es ideal para mantener el área de trabajo organizada y estilizada.'),

-- Sofás
('Sofá de cuero', 399.99, (SELECT id FROM tipo_producto WHERE nombre = 'Sofás'), 'sofa-cuero.jpg', 'Sofá de cuero muy cómodo, con un diseño atemporal que se adapta a cualquier sala de estar. Fabricado con cuero de alta calidad, este sofá ofrece durabilidad y estilo, proporcionando un lugar perfecto para relajarse.'),
('Sofá de tela', 349.50, (SELECT id FROM tipo_producto WHERE nombre = 'Sofás'), 'sofa-tela.jpg', 'Sofá de tela de color gris, con un diseño moderno y cómodo. La tela es suave al tacto y resistente al desgaste, mientras que el relleno de alta densidad proporciona un confort superior. Ideal para cualquier sala de estar.'),
('Sofá cama', 449.99, (SELECT id FROM tipo_producto WHERE nombre = 'Sofás'), 'sofa-cama.jpg', 'Sofá cama funcional y elegante, perfecto para ahorrar espacio en apartamentos y estudios. Su mecanismo fácil de usar permite convertirlo en una cama cómoda en cuestión de segundos.'),
('Sofá seccional', 499.75, (SELECT id FROM tipo_producto WHERE nombre = 'Sofás'), 'sofa-seccional.jpg', 'Sofá seccional de gran tamaño, ideal para familias y espacios amplios. Su diseño modular permite configurarlo de diversas maneras para adaptarse a tu sala de estar, ofreciendo comodidad y versatilidad.'),

-- Decoración
('Lámpara de pie', 75.00, (SELECT id FROM tipo_producto WHERE nombre = 'Decoración'), 'lampara-pie.jpg', 'Lámpara de pie con diseño minimalista, ideal para añadir un toque de elegancia a cualquier habitación. Su estructura delgada y estable es perfecta para iluminar espacios de lectura o salones modernos.'),
('Cuadro abstracto', 44.99, (SELECT id FROM tipo_producto WHERE nombre = 'Decoración'), 'cuadro-abstracto.jpg', 'Cuadro abstracto con vibrantes colores, ideal para darle vida y personalidad a cualquier espacio. Este cuadro es perfecto para oficinas, salas de estar o dormitorios que buscan un toque de modernidad.'),
('Reloj de pared', 29.99, (SELECT id FROM tipo_producto WHERE nombre = 'Decoración'), 'reloj-pared.jpg', 'Reloj de pared con un diseño simple y elegante, ideal para cualquier habitación de la casa. Su mecanismo silencioso y su diseño moderno lo convierten en una excelente opción para mantener la puntualidad sin sacrificar el estilo.'),
('Jarrón de cerámica', 39.99, (SELECT id FROM tipo_producto WHERE nombre = 'Decoración'), 'jarron-ceramica.jpg', 'Jarrón de cerámica de diseño minimalista, perfecto para flores frescas o secas. Su acabado mate y su forma elegante lo hacen un complemento ideal para cualquier estilo de decoración, añadiendo un toque de sofisticación a tu hogar.');

-- Insertar datos en la tabla tienda
WITH direccion_data_1 AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) 
    VALUES ('Calle Mayor, 1', 'Madrid', 'Madrid', '29990') 
    RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) 
VALUES ('Ukun Online', (SELECT id FROM direccion_data_1), '912345670');

WITH direccion_data_2 AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) 
    VALUES ('Vía Complutense, 1', 'Madrid', 'Alcalá de Henares', '28801') 
    RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) 
VALUES ('Ukun Alcalá de Henares', (SELECT id FROM direccion_data_2), '912345678');

WITH direccion_data_3 AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) 
    VALUES ('Avenida de la Constitución, 12', 'Madrid', 'Torrejón de Ardoz', '28103') 
    RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) 
VALUES ('Ukun Torrejón de Ardoz', (SELECT id FROM direccion_data_3), '912345679');

WITH direccion_data_4 AS (
    INSERT INTO direccion (calle, provincia, ciudad, codigo_postal) 
    VALUES ('Avenida Diagonal, 4', 'Barcelona', 'Barcelona', '56123') 
    RETURNING id
)
INSERT INTO tienda (nombre, direccion_id, telefono) 
VALUES ('Ukun Barcelona', (SELECT id FROM direccion_data_4), '934567890');

-- Insertar datos en la tabla empleado
-- Ukun Online
WITH empleado_data_1 AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES 
    ('salvador.oton@ukun.com', '123456', 'Salvador', 'Oton'),
    ('laura.martinez@ukun.com', '123456', 'Laura', 'Martínez'),
    ('carlos.lopez@ukun.com', '123456', 'Carlos', 'López')
    RETURNING id, correo
),
tienda_data_1 AS (
    SELECT id FROM tienda WHERE nombre = 'Ukun Online'
)
INSERT INTO empleado (id, admin, empleo, tienda_id)
SELECT 
    id, 
    CASE WHEN correo = 'salvador.oton@ukun.com' THEN TRUE ELSE FALSE END, 
    CASE WHEN correo = 'salvador.oton@ukun.com' THEN 'Gerente' ELSE 'Vendedor' END, 
    (SELECT id FROM tienda_data_1) 
FROM empleado_data_1;

-- Ukun Alcalá de Henares
WITH empleado_data_2 AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES 
    ('joel.sandoval@ukun.com', '123456', 'Joel', 'Sandoval'),
    ('daniel.garcia@ukun.com', '123456', 'Daniel', 'García'),
    ('antonio.ruiz@ukun.com', '123456', 'Antonio', 'Ruiz')
    RETURNING id, correo
),
tienda_data_2 AS (
    SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'
)
INSERT INTO empleado (id, admin, empleo, tienda_id)
SELECT 
    id, 
    CASE WHEN correo = 'joel.sandoval@ukun.com' THEN TRUE ELSE FALSE END, 
    CASE WHEN correo = 'joel.sandoval@ukun.com' THEN 'Gerente' ELSE 'Vendedor' END, 
    (SELECT id FROM tienda_data_2) 
FROM empleado_data_2;

-- Ukun Torrejón de Ardoz
WITH empleado_data_3 AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES 
    ('miguel.virtus@ukun.com', '123456', 'Miguel', 'Virtus'),
    ('alvaro.mazo@ukun.com', '123456', 'Álvaro', 'Mazo'),
    ('juan.martin@ukun.com', '123456', 'Juan', 'Martín')
    RETURNING id, correo
),
tienda_data_3 AS (
    SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'
)
INSERT INTO empleado (id, admin, empleo, tienda_id)
SELECT 
    id, 
    CASE WHEN correo = 'miguel.virtus@ukun.com' THEN TRUE ELSE FALSE END, 
    CASE WHEN correo = 'miguel.virtus@ukun.com' THEN 'Gerente' ELSE 'Vendedor' END, 
    (SELECT id FROM tienda_data_3) 
FROM empleado_data_3;

-- Ukun Barcelona
WITH empleado_data_4 AS (
    INSERT INTO cuenta (correo, contrasenna, nombre, apellido)
    VALUES 
    ('javier.castillo@ukun.com', '123456', 'Javier', 'Castillo'),
    ('marcos.rincon@ukun.com', '123456', 'Marcos', 'Rincón'),
    ('david.perez@ukun.com', '123456', 'David', 'Pérez')
    RETURNING id, correo
),
tienda_data_4 AS (
    SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'
)
INSERT INTO empleado (id, admin, empleo, tienda_id)
SELECT 
    id, 
    CASE WHEN correo = 'javier.castillo@ukun.com' THEN TRUE ELSE FALSE END, 
    CASE WHEN correo = 'javier.castillo@ukun.com' THEN 'Gerente' ELSE 'Vendedor' END, 
    (SELECT id FROM tienda_data_4) 
FROM empleado_data_4;


-- Insertar datos en la tabla stock_tienda
INSERT INTO stock_tienda (producto_id, tienda_id, stock) VALUES
-- Ukun Online
((SELECT id FROM producto WHERE nombre = 'Mesa de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 50),
((SELECT id FROM producto WHERE nombre = 'Mesa de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 40),
((SELECT id FROM producto WHERE nombre = 'Mesa de comedor extendible'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 20),
((SELECT id FROM producto WHERE nombre = 'Mesa auxiliar de metal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 35),
((SELECT id FROM producto WHERE nombre = 'Silla de plástico'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 100),
((SELECT id FROM producto WHERE nombre = 'Silla de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 75),
((SELECT id FROM producto WHERE nombre = 'Silla ergonómica de oficina'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 30),
((SELECT id FROM producto WHERE nombre = 'Silla de comedor tapizada'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 50),
((SELECT id FROM producto WHERE nombre = 'Cama individual'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 25),
((SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 15),
((SELECT id FROM producto WHERE nombre = 'Cama de día'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 10),
((SELECT id FROM producto WHERE nombre = 'Litera infantil'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 5),
((SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 20),
((SELECT id FROM producto WHERE nombre = 'Escritorio de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 25),
((SELECT id FROM producto WHERE nombre = 'Escritorio compacto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 35),
((SELECT id FROM producto WHERE nombre = 'Escritorio con estantería'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 10),
((SELECT id FROM producto WHERE nombre = 'Sofá de cuero'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 10),
((SELECT id FROM producto WHERE nombre = 'Sofá de tela'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 15),
((SELECT id FROM producto WHERE nombre = 'Sofá cama'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 5),
((SELECT id FROM producto WHERE nombre = 'Sofá seccional'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 3),
((SELECT id FROM producto WHERE nombre = 'Lámpara de pie'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 60),
((SELECT id FROM producto WHERE nombre = 'Cuadro abstracto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 80),
((SELECT id FROM producto WHERE nombre = 'Reloj de pared'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 70),
((SELECT id FROM producto WHERE nombre = 'Jarrón de cerámica'), (SELECT id FROM tienda WHERE nombre = 'Ukun Online'), 50),

-- Ukun Alcalá de Henares
((SELECT id FROM producto WHERE nombre = 'Mesa de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 30),
((SELECT id FROM producto WHERE nombre = 'Mesa de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 25),
((SELECT id FROM producto WHERE nombre = 'Mesa de comedor extendible'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 15),
((SELECT id FROM producto WHERE nombre = 'Mesa auxiliar de metal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 20),
((SELECT id FROM producto WHERE nombre = 'Silla de plástico'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 80),
((SELECT id FROM producto WHERE nombre = 'Silla de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 60),
((SELECT id FROM producto WHERE nombre = 'Silla ergonómica de oficina'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 25),
((SELECT id FROM producto WHERE nombre = 'Silla de comedor tapizada'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 40),
((SELECT id FROM producto WHERE nombre = 'Cama individual'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 20),
((SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 10),
((SELECT id FROM producto WHERE nombre = 'Cama de día'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 8),
((SELECT id FROM producto WHERE nombre = 'Litera infantil'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 4),
((SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 15),
((SELECT id FROM producto WHERE nombre = 'Escritorio de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 20),
((SELECT id FROM producto WHERE nombre = 'Escritorio compacto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 30),
((SELECT id FROM producto WHERE nombre = 'Escritorio con estantería'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 8),
((SELECT id FROM producto WHERE nombre = 'Sofá de cuero'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 8),
((SELECT id FROM producto WHERE nombre = 'Sofá de tela'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 10),
((SELECT id FROM producto WHERE nombre = 'Sofá cama'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 4),
((SELECT id FROM producto WHERE nombre = 'Sofá seccional'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 2),
((SELECT id FROM producto WHERE nombre = 'Lámpara de pie'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 50),
((SELECT id FROM producto WHERE nombre = 'Cuadro abstracto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 70),
((SELECT id FROM producto WHERE nombre = 'Reloj de pared'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 60),
((SELECT id FROM producto WHERE nombre = 'Jarrón de cerámica'), (SELECT id FROM tienda WHERE nombre = 'Ukun Alcalá de Henares'), 40),

-- Ukun Torrejón de Ardoz
((SELECT id FROM producto WHERE nombre = 'Mesa de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 25),
((SELECT id FROM producto WHERE nombre = 'Mesa de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 20),
((SELECT id FROM producto WHERE nombre = 'Mesa de comedor extendible'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 10),
((SELECT id FROM producto WHERE nombre = 'Mesa auxiliar de metal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 18),
((SELECT id FROM producto WHERE nombre = 'Silla de plástico'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 70),
((SELECT id FROM producto WHERE nombre = 'Silla de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 50),
((SELECT id FROM producto WHERE nombre = 'Silla ergonómica de oficina'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 20),
((SELECT id FROM producto WHERE nombre = 'Silla de comedor tapizada'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 30),
((SELECT id FROM producto WHERE nombre = 'Cama individual'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 15),
((SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 8),
((SELECT id FROM producto WHERE nombre = 'Cama de día'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 6),
((SELECT id FROM producto WHERE nombre = 'Litera infantil'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 3),
((SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 10),
((SELECT id FROM producto WHERE nombre = 'Escritorio de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 15),
((SELECT id FROM producto WHERE nombre = 'Escritorio compacto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 20),
((SELECT id FROM producto WHERE nombre = 'Escritorio con estantería'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 5),
((SELECT id FROM producto WHERE nombre = 'Sofá de cuero'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 5),
((SELECT id FROM producto WHERE nombre = 'Sofá de tela'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 8),
((SELECT id FROM producto WHERE nombre = 'Sofá cama'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 3),
((SELECT id FROM producto WHERE nombre = 'Sofá seccional'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 1),
((SELECT id FROM producto WHERE nombre = 'Lámpara de pie'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 40),
((SELECT id FROM producto WHERE nombre = 'Cuadro abstracto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 60),
((SELECT id FROM producto WHERE nombre = 'Reloj de pared'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 50),
((SELECT id FROM producto WHERE nombre = 'Jarrón de cerámica'), (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz'), 30),

-- Ukun Barcelona
((SELECT id FROM producto WHERE nombre = 'Mesa de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 20),
((SELECT id FROM producto WHERE nombre = 'Mesa de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 15),
((SELECT id FROM producto WHERE nombre = 'Mesa de comedor extendible'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 8),
((SELECT id FROM producto WHERE nombre = 'Mesa auxiliar de metal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 12),
((SELECT id FROM producto WHERE nombre = 'Silla de plástico'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 50),
((SELECT id FROM producto WHERE nombre = 'Silla de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 35),
((SELECT id FROM producto WHERE nombre = 'Silla ergonómica de oficina'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 15),
((SELECT id FROM producto WHERE nombre = 'Silla de comedor tapizada'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 25),
((SELECT id FROM producto WHERE nombre = 'Cama individual'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 10),
((SELECT id FROM producto WHERE nombre = 'Cama de matrimonio'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 5),
((SELECT id FROM producto WHERE nombre = 'Cama de día'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 4),
((SELECT id FROM producto WHERE nombre = 'Litera infantil'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 2),
((SELECT id FROM producto WHERE nombre = 'Escritorio de cristal'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 8),
((SELECT id FROM producto WHERE nombre = 'Escritorio de madera'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 10),
((SELECT id FROM producto WHERE nombre = 'Escritorio compacto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 15),
((SELECT id FROM producto WHERE nombre = 'Escritorio con estantería'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 4),
((SELECT id FROM producto WHERE nombre = 'Sofá de cuero'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 4),
((SELECT id FROM producto WHERE nombre = 'Sofá de tela'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 5),
((SELECT id FROM producto WHERE nombre = 'Sofá cama'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 2),
((SELECT id FROM producto WHERE nombre = 'Sofá seccional'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 1),
((SELECT id FROM producto WHERE nombre = 'Lámpara de pie'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 30),
((SELECT id FROM producto WHERE nombre = 'Cuadro abstracto'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 40),
((SELECT id FROM producto WHERE nombre = 'Reloj de pared'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 35),
((SELECT id FROM producto WHERE nombre = 'Jarrón de cerámica'), (SELECT id FROM tienda WHERE nombre = 'Ukun Barcelona'), 20);


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
INSERT INTO cliente (id, id_direccion, id_tarjeta, tienda_fav) VALUES
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
),
tarjeta_data AS (
    INSERT INTO tarjeta (numero, fecha_expiracion, cvv)
    VALUES ('2345678901234567', '2026-11-01', '234') RETURNING id
)
INSERT INTO cliente (id, id_direccion, id_tarjeta, tienda_fav) VALUES
(
    (SELECT id FROM cliente_data),
    (SELECT id FROM direccion_data),
    (SELECT id FROM tarjeta_data),
    (SELECT id FROM tienda WHERE nombre = 'Ukun Torrejón de Ardoz')
);
