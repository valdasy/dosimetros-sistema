-- Agrega el RUT del cliente (opcional).
--
-- Se guarda como texto libre (VARCHAR) para tolerar distintos formatos
-- (con/sin puntos y guion) y clientes sin RUT (ej. "Por asignar"). No se
-- fuerza unicidad para no romper cargas con datos incompletos o repetidos.
ALTER TABLE cliente ADD COLUMN rut VARCHAR(20) NULL;
