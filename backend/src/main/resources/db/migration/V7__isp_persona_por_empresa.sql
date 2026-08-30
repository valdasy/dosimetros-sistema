-- El match de COD CARGO / COD PRAC pasa de ser por RUT global a ser por
-- (empresa + RUT): cada laboratorio (Dosimet | Photomat) aprende y reutiliza
-- sus propias personas a partir del trimestre anterior. La maestra
-- isp_persona_codigo está vacía en este punto, así que se recompone su clave
-- única sin pérdida de datos.

-- Quita la unicidad global por RUT.
ALTER TABLE isp_persona_codigo DROP INDEX uk_isp_persona_rut;

-- Agrega la empresa (laboratorio) como parte de la identidad de la persona.
ALTER TABLE isp_persona_codigo
    ADD COLUMN empresa VARCHAR(20) NOT NULL AFTER id;

-- Nueva clave única: una persona (RUT) por laboratorio.
ALTER TABLE isp_persona_codigo
    ADD CONSTRAINT uk_isp_persona_empresa_rut UNIQUE (empresa, rut);
