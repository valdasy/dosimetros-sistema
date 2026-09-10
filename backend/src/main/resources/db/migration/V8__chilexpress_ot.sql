-- Módulo Seguimiento Chilexpress (autocontenido).
--
-- Registra las Órdenes de Transporte (OT) exportadas desde el portal de
-- Chilexpress. Módulo independiente: no tiene relación (FK) con el resto del
-- sistema; la empresa (Dosimet | Photomat) y el "cliente" (destinatario /
-- referencia) se guardan como texto propio.
--
-- No se fuerza unicidad por (empresa, nro_ot): Chilexpress puede reutilizar un
-- número de OT antiguo tras purgar (~cada 3 meses). La decisión de actualizar
-- una OT existente o registrar una nueva se hace en la app por "ventana de
-- tiempo" (ver ChilexpressImportService). Por eso solo hay un índice de apoyo.

CREATE TABLE chilexpress_ot (
    id                    INT          NOT NULL AUTO_INCREMENT,
    empresa               VARCHAR(20)  NOT NULL,          -- Dosimet | Photomat

    -- Identidad y datos fijos del envío
    nro_ot                VARCHAR(40)  NOT NULL,
    ot_padre              VARCHAR(40),
    nro_referencia        VARCHAR(300),
    nombre_destinatario   VARCHAR(300),
    destino               VARCHAR(150),
    direccion             VARCHAR(400),
    servicio              VARCHAR(120),                   -- Tiempo de entrega o servicio
    valor_declarado       INT,
    oficina_origen        VARCHAR(150),
    tipo_admision         VARCHAR(60),
    tipo_entrega          VARCHAR(60),

    -- Campos que se ACTUALIZAN en cada carga (estado y recepción)
    estado                VARCHAR(120),
    oficina_destino       VARCHAR(150),
    receptor              VARCHAR(200),
    rut_receptor          VARCHAR(30),
    fecha_primer_intento  DATE,
    fecha_entrega         DATE,
    hora_entrega          VARCHAR(10),
    certificado_entrega   VARCHAR(500),

    -- Control: periodo (rango del archivo) y auditoría
    periodo_desde         DATE,
    periodo_hasta         DATE,
    creado_en             DATETIME     NOT NULL,
    actualizado_en        DATETIME     NOT NULL,

    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX idx_chilexpress_empresa_ot ON chilexpress_ot (empresa, nro_ot);
CREATE INDEX idx_chilexpress_fecha_entrega ON chilexpress_ot (fecha_entrega);
