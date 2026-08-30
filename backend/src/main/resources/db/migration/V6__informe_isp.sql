-- Módulo Informe ISP (Registro Nacional de Dosis - RND).
-- Ver especificación funcional en docs/INFORME_ISP.md.
--
-- Catálogos (isp_codigo_servicio, isp_clasificador) se siembran aquí porque no son
-- datos personales. Las maestras aprendidas (isp_persona_codigo, isp_cliente_tecnologia)
-- se crean VACÍAS y se cargan desde la app (contienen datos personales, no van en git).

-- Códigos de Servicio RND: (empresa, tecnología, magnitud, periodicidad) -> código.
CREATE TABLE isp_codigo_servicio (
    id           INT          NOT NULL AUTO_INCREMENT,
    empresa_id   INT          NOT NULL,
    tecnologia   VARCHAR(20)  NOT NULL,        -- TLD | OSL | FILM
    magnitud     VARCHAR(10)  NOT NULL,        -- HP10 | HP0.07 | HP3
    periodicidad VARCHAR(20)  NOT NULL,        -- TRIMESTRAL | BIMENSUAL | MENSUAL
    codigo       INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_isp_codserv_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT uk_isp_codserv UNIQUE (empresa_id, tecnologia, magnitud, periodicidad)
) ENGINE = InnoDB;

-- Clasificadores del ISP (cargo, práctica, localización, sector): código + nombre.
CREATE TABLE isp_clasificador (
    id     INT          NOT NULL AUTO_INCREMENT,
    tipo   VARCHAR(20)  NOT NULL,              -- CARGO | PRACTICA | LOCALIZACION | SECTOR
    codigo INT          NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_isp_clasificador UNIQUE (tipo, codigo)
) ENGINE = InnoDB;

-- Maestra por persona: RUT -> cod_cargo, cod_prac. Se importa desde la app.
CREATE TABLE isp_persona_codigo (
    id        INT         NOT NULL AUTO_INCREMENT,
    rut       VARCHAR(15) NOT NULL,
    cod_cargo INT,
    cod_prac  INT,
    PRIMARY KEY (id),
    CONSTRAINT uk_isp_persona_rut UNIQUE (rut)
) ENGINE = InnoDB;

-- Maestra por cliente: (empresa, RUT entidad) -> tecnología. Se importa desde la app.
CREATE TABLE isp_cliente_tecnologia (
    id          INT         NOT NULL AUTO_INCREMENT,
    empresa_id  INT         NOT NULL,
    rut_entidad VARCHAR(15) NOT NULL,
    tecnologia  VARCHAR(20) NOT NULL,          -- TLD | OSL | FILM
    PRIMARY KEY (id),
    CONSTRAINT fk_isp_clitec_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT uk_isp_clitec UNIQUE (empresa_id, rut_entidad)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- Semilla: Códigos de Servicio RND
-- ---------------------------------------------------------------------------
INSERT INTO isp_codigo_servicio (empresa_id, tecnologia, magnitud, periodicidad, codigo)
SELECT e.id, x.tecnologia, x.magnitud, x.periodicidad, x.codigo
FROM (
    SELECT 'Dosimet'  AS empresa, 'TLD'  AS tecnologia, 'HP10'   AS magnitud, 'TRIMESTRAL' AS periodicidad, 3  AS codigo UNION ALL
    SELECT 'Dosimet', 'TLD',  'HP10',   'BIMENSUAL',  4  UNION ALL
    SELECT 'Dosimet', 'TLD',  'HP10',   'MENSUAL',    5  UNION ALL
    SELECT 'Dosimet', 'TLD',  'HP0.07', 'TRIMESTRAL', 6  UNION ALL
    SELECT 'Dosimet', 'TLD',  'HP0.07', 'BIMENSUAL',  7  UNION ALL
    SELECT 'Dosimet', 'OSL',  'HP10',   'TRIMESTRAL', 50 UNION ALL
    SELECT 'Dosimet', 'OSL',  'HP10',   'MENSUAL',    51 UNION ALL
    SELECT 'Dosimet', 'OSL',  'HP0.07', 'TRIMESTRAL', 52 UNION ALL
    SELECT 'Dosimet', 'OSL',  'HP0.07', 'MENSUAL',    53 UNION ALL
    SELECT 'Dosimet', 'OSL',  'HP3',    'TRIMESTRAL', 54 UNION ALL
    SELECT 'Dosimet', 'OSL',  'HP3',    'MENSUAL',    55 UNION ALL
    SELECT 'Photomat', 'TLD',  'HP10',   'TRIMESTRAL', 43 UNION ALL
    SELECT 'Photomat', 'TLD',  'HP10',   'MENSUAL',    45 UNION ALL
    SELECT 'Photomat', 'TLD',  'HP0.07', 'TRIMESTRAL', 44 UNION ALL
    SELECT 'Photomat', 'TLD',  'HP0.07', 'MENSUAL',    46 UNION ALL
    SELECT 'Photomat', 'FILM', 'HP10',   'TRIMESTRAL', 2
) x
JOIN empresa e ON e.nombre = x.empresa;

-- ---------------------------------------------------------------------------
-- Semilla: Clasificadores ISP
-- ---------------------------------------------------------------------------
INSERT INTO isp_clasificador (tipo, codigo, nombre) VALUES
-- 8.- Cargos con Responsabilidades en el Trabajo con Radiaciones
('CARGO', 0,  'Desconocido'),
('CARGO', 1,  'Encargado Protección Radiológica'),
('CARGO', 2,  'Inspector'),
('CARGO', 3,  'Técnico'),
('CARGO', 4,  'Directivo'),
('CARGO', 5,  'Operador'),
('CARGO', 6,  'Supervisor'),
('CARGO', 7,  'Investigador'),
('CARGO', 8,  'Profesor'),
('CARGO', 9,  'Estudiante'),
('CARGO', 10, 'Médico'),
('CARGO', 11, 'Físico Médico'),
('CARGO', 12, 'Tecnólogo Médico'),
('CARGO', 13, 'Odontólogo'),
('CARGO', 14, 'Veterinario'),
('CARGO', 15, 'Personal de Enfermería'),
('CARGO', 16, 'Personal de Limpieza'),
('CARGO', 17, 'Personal de Mantenimiento'),
('CARGO', 18, 'Personal Auxiliar o Apoyo'),
('CARGO', 19, 'Radiofarmacéutico'),
('CARGO', 20, 'Químico Analista'),
('CARGO', 21, 'Electrónico'),
('CARGO', 22, 'Instrumentista'),
('CARGO', 23, 'Chofer- Transportista'),
-- 7.- Prácticas asociadas al empleo de las radiaciones ionizantes
('PRACTICA', 0,  'Desconocida'),
('PRACTICA', 1,  'Radioterapia'),
('PRACTICA', 2,  'Medicina Nuclear'),
('PRACTICA', 3,  'Radioinmunoanálisis'),
('PRACTICA', 4,  'Rayos X Diagnóstico médico'),
('PRACTICA', 5,  'Rayos X Intervencionismo (Pabellón)'),
('PRACTICA', 6,  'Rayos X Dental'),
('PRACTICA', 7,  'Rayos X Veterinario'),
('PRACTICA', 8,  'Radiografía Industrial – Rx.'),
('PRACTICA', 9,  'Gammagrafía'),
('PRACTICA', 10, 'Aceleradores Lineales para uso Industrial'),
('PRACTICA', 11, 'Medidores Nucleares Fijos'),
('PRACTICA', 12, 'Medidores Nucleares Móviles'),
('PRACTICA', 13, 'Perfilaje de Pozos'),
('PRACTICA', 14, 'Producción de Radioisótopos'),
('PRACTICA', 15, 'Control de bultos, vehículos y personas'),
('PRACTICA', 16, 'Irradiador Industrial'),
('PRACTICA', 17, 'Reactor de Investigación'),
('PRACTICA', 18, 'Técnicas Analíticas'),
('PRACTICA', 19, 'Calibración Dosimétrica'),
('PRACTICA', 20, 'Gestión de Desechos Radiactivos'),
('PRACTICA', 21, 'Almacenamiento Temporal de Material Radiactivo y Fuentes'),
('PRACTICA', 22, 'Transporte Terrestre'),
('PRACTICA', 23, 'Importación, Exportación y Comercialización'),
('PRACTICA', 24, 'Servicios Técnicos'),
('PRACTICA', 25, 'Supervisión Reguladora'),
('PRACTICA', 26, 'Actividades con exposición sustancial al RADON'),
-- 4.- Localización de Dosímetros (dosimetría externa)
('LOCALIZACION', 0, '- no -'),
('LOCALIZACION', 1, 'Tórax'),
('LOCALIZACION', 2, 'Extremidades (dedo)'),
('LOCALIZACION', 3, 'Extremidades (brazo, muñeca)'),
('LOCALIZACION', 4, 'Cabeza (cristalino)'),
-- 2.- Sector al que pertenecen las entidades
('SECTOR', 1, 'Medicina'),
('SECTOR', 2, 'Industria'),
('SECTOR', 3, 'Investigación y Docencia'),
('SECTOR', 4, 'Supervisión y Seguridad (SSO)'),
('SECTOR', 5, 'Servicios');
