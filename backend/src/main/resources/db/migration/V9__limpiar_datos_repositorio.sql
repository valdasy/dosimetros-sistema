-- Limpieza única de los datos "sucios" de prueba del módulo Repositorio
-- (dosímetros / tareas / asignaciones / clientes / ejecutivos).
--
-- Contexto: los datos actuales del módulo eran de prueba y se ensuciaron
-- durante los testeos. Esta migración deja esas tablas vacías para volver a
-- cargar la información real con el importador de Excel.
--
-- NO toca:
--   * Catálogos base: rol, empresa, tipo_dosimetro, tipo_porta.
--   * Usuarios (usuario), incluido el administrador.
--   * Módulo Informe ISP ni módulo Seguimiento Chilexpress (chilexpress_ot).
--
-- Nota: en una base nueva (entorno limpio) estas tablas ya están vacías, por lo
-- que la migración simplemente no borra nada (es segura de re-aplicar).

-- El orden respeta las llaves foráneas: primero la tabla hija (ASIGNACION),
-- luego los dosímetros y sus tareas, y por último clientes y ejecutivos.

-- 1. Asignaciones (hija de dosímetro, cliente, ejecutivo, tarea…).
DELETE FROM ASIGNACION;

-- 2. Inventario de dosímetros.
DELETE FROM dosimetro;

-- 3. Tareas / repositorios asociados a los dosímetros.
DELETE FROM tarea;

-- 4. Clientes.
DELETE FROM cliente;

-- 5. Ejecutivos, EXCEPTO los que tienen una cuenta de usuario asociada
--    (usuario.ejecutivo_id): borrarlos rompería el acceso de esos usuarios.
DELETE FROM ejecutivo
WHERE id NOT IN (
    SELECT ejecutivo_id FROM usuario WHERE ejecutivo_id IS NOT NULL
);

-- Reinicia los contadores AUTO_INCREMENT de las tablas que quedaron vacías,
-- para que los datos reales empiecen desde el id 1.
-- (ejecutivo se omite porque podrían quedar filas ligadas a usuarios.)
ALTER TABLE ASIGNACION AUTO_INCREMENT = 1;
ALTER TABLE dosimetro  AUTO_INCREMENT = 1;
ALTER TABLE tarea      AUTO_INCREMENT = 1;
ALTER TABLE cliente    AUTO_INCREMENT = 1;
