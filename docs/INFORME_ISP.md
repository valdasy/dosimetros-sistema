# Módulo Informe ISP (Registro Nacional de Dosis — RND)

> Genera el **informe dosimétrico trimestral** que Dosimet y Photomat deben entregar
> al **ISP** (registro RND), a partir del *Informe de Dosis* crudo que se descarga de
> la plataforma dosimétrica. Automatiza la **asignación de códigos** (servicio, práctica
> y cargo) y produce el Excel con el formato exacto que exige la autoridad.

Este documento es la **especificación funcional** del módulo: el diccionario de datos,
las reglas de negocio y los mapeos. Es la fuente de verdad; el código lo implementa.

---

## 1. Panorama

Cada trimestre se descarga, **por empresa** (Dosimet y Photomat, mismo formato), un
*Informe de Dosis* crudo. El programa:

1. **Limpia y valida** el informe (Etapa 1).
2. **Asigna** los códigos `COD SERV`, `COD PRAC`, `COD CARGO` (Etapas 2–3).
3. **Genera** el libro Excel del ISP con las pestañas `TOES` y `DOSIS` (Etapa 4),
   más una hoja `REVISION` con las inconsistencias detectadas.

```
Informe de Dosis crudo (Dosimet | Photomat)
        │
   [1] limpieza + validaciones ─────────► hoja REVISION (no bloquea)
        │
   [2/3] asignación de códigos
        │   • COD SERV  ← regla (empresa × tecnología × magnitud × periodicidad)
        │   • COD PRAC  ← maestra por RUT (o sugerencia por cliente)
        │   • COD CARGO ← maestra por RUT (o sugerencia por cliente)
        │   • OBSERVA + Dosis ← mapeo de siglas / 2 decimales
        │
   [4] Excel ISP (TOES + DOSIS + REVISION)
```

Las **maestras** (`RUT → cargo/práctica`, `cliente → tecnología`) se **aprenden** de un
informe ISP ya entregado (p. ej. el 1er Trimestre 2026) y se **importan a la BD** desde
la app; se reutilizan y editan cada trimestre. No se versionan en git (contienen datos
personales).

---

## 2. Entrada — *Informe de Dosis* crudo

Hoja única `Informe_dosis`, encabezados en la fila 1. Columnas:

| Columna | Uso |
|---|---|
| IdServicio | id del servicio (fila) |
| Cliente | razón social (orden de salida) |
| DocumentoCliente | **RUT de la entidad** (empleador) → se une a la maestra de tecnología |
| **Rut** | **RUT de la persona** → filtro y clave de maestras |
| Usuario | nombre de la persona (→ TOES) |
| Genero | Masculino/Femenino → Sexo M/F |
| Area | informativo |
| TipoDosimetro | AMBIENTAL · CUERPO COMPLETO/PERSONAL · DE CONTROL · DE REFERENCIA · EXTREMIDAD |
| **Dosimetro** | código físico → filtro |
| Reporte | informativo |
| FechaInicio / FechaFin | período de monitoreo (dd/mm/aaaa) |
| **Dosis Profundidad** | dosis Hp(10) — numérica o sigla |
| **Dosis Piel** | dosis Hp(0.07) — numérica o sigla |
| **Dosis Cristalino** | dosis Hp(3) — numérica o sigla |
| DosisIntegrada | **se descarta** |
| Sede | informativo |
| **Ubicacion** | determina la **magnitud** (ver §4) |
| Periodicidad | MENSUAL · BIMESTRAL · TRIMESTRAL |
| Estado Reporte | **se descarta** |

**Siglas de dosis** (aplican a las 3 columnas de dosis): `DND` (no devuelto),
`DSU` (sin uso), `MNR` (menor al nivel de registro), `DD` (dañado), `DE` (extraviado),
`NR` (no registra / fuera de plazo).

---

## 3. Etapa 1 — Limpieza y validaciones

**Se elimina (silencioso):**
- Filas sin `Rut` **o** sin `Dosimetro` (dosímetros ambientales/control/referencia y
  personas sin dosímetro asignado no van al informe).
- Se descartan las columnas `DosisIntegrada` y `Estado Reporte`.

**Resultado ordenado por `Cliente`.**

**Inconsistencias → hoja `REVISION` (no bloquea la generación):**
- Persona (con `Rut`) con `TipoDosimetro` = AMBIENTAL / DE CONTROL / DE REFERENCIA.
- No-persona (sin `Rut`) con `TipoDosimetro` = CUERPO COMPLETO/PERSONAL / EXTREMIDAD.
- Persona con `Ubicacion` no mapeable a magnitud.
- Persona nueva (RUT ausente en la maestra) → se **sugiere** cargo/práctica del cliente.
- Cliente sin tecnología conocida → se usa el default de la empresa y se marca.

---

## 4. Magnitud y localización (desde `Ubicacion`)

Una fila = un dosímetro = **una** magnitud. Una persona con varias magnitudes aparece
en varias filas.

| Ubicacion | Magnitud | Columna de dosis | Localización ISP (clas. 4) |
|---|---|---|---|
| PERSONAL, ABDOMINAL | **HP10** | Dosis Profundidad | 1 (Tórax) |
| ANILLO / DEDO | **HP0.07** | Dosis Piel | 2 (Extremidades, dedo) |
| PULSERA / MUÑECA / BRAZO | **HP0.07** | Dosis Piel | 3 (Extremidades, brazo/muñeca) |
| TIROIDEO, CRISTALINO | **HP3** | Dosis Cristalino | 4 (Cabeza, cristalino) |

> La distinción dedo/muñeca la marca el usuario en `Ubicacion` antes de procesar.
> `ANILLO/PULSERA` sin marcar → magnitud HP0.07, localización 2 (dedo) + alerta.

---

## 5. Asignación de códigos

### COD SERV — por regla
`COD SERV = f(empresa, tecnología, magnitud, periodicidad)` vía la tabla de códigos RND
(`isp_codigo_servicio`).

- **empresa**: la del informe procesado (Dosimet | Photomat).
- **tecnología**: de la maestra `cliente → tecnología` (por RUT de entidad y empresa);
  si el cliente no está, se usa el default de la empresa y se marca.
- **magnitud**: de `Ubicacion` (§4).
- **periodicidad**: de `Periodicidad` (`BIMESTRAL` del informe = `BIMENSUAL` del código).

Tabla de códigos RND:

| Empresa | Tecnología | Magnitud | Periodicidad | Código |
|---|---|---|---|---|
| Dosimet | TLD | HP10 | Trimestral | 3 |
| Dosimet | TLD | HP10 | Bimensual | 4 |
| Dosimet | TLD | HP10 | Mensual | 5 |
| Dosimet | TLD | HP0.07 | Trimestral | 6 |
| Dosimet | TLD | HP0.07 | Bimensual | 7 |
| Dosimet | OSL | HP10 | Trimestral | 50 |
| Dosimet | OSL | HP10 | Mensual | 51 |
| Dosimet | OSL | HP0.07 | Trimestral | 52 |
| Dosimet | OSL | HP0.07 | Mensual | 53 |
| Dosimet | OSL | HP3 | Trimestral | 54 |
| Dosimet | OSL | HP3 | Mensual | 55 |
| Photomat | TLD | HP10 | Trimestral | 43 |
| Photomat | TLD | HP10 | Mensual | 45 |
| Photomat | TLD | HP0.07 | Trimestral | 44 |
| Photomat | TLD | HP0.07 | Mensual | 46 |
| Photomat | FILM | HP10 | Trimestral | 2 |

### COD PRAC y COD CARGO — por maestra de persona
De la maestra `RUT → cod_prac / cod_cargo` (`isp_persona_codigo`), aprendida de un Q1
entregado. Verificado: en Q1 el cargo es 100% estable por persona y la práctica 99,96%.

- **Persona conocida** → se asignan sus códigos.
- **Persona nueva** → se **sugiere** la práctica/cargo más frecuente del mismo cliente
  (RUT de entidad) entre las personas ya conocidas, marcándola en `REVISION`.
- Sin sugerencia posible → celda vacía + alerta.

---

## 6. Salida — Excel del ISP

Libro con encabezados en filas intermedias y columnas separadoras `B1..B9`.

### Hoja `TOES` (1 fila por persona única)
Encabezados en fila 6, datos desde fila 8.

| Col | Campo |
|---|---|
| C | RUN (Rut persona) |
| E | Nombre y Apellido |
| G | Sexo (F/M) |
| I | Fecha de Nacimiento (si existe) |
| K | País |

### Hoja `DOSIS` (1 fila por dosímetro/magnitud)
Encabezados en fila 5, datos desde fila 7.

| Col | Campo | Origen |
|---|---|---|
| C | RUN | `Rut` |
| E | COD SERV | regla §5 |
| G | RUT (entidad) | `DocumentoCliente` |
| I | COD PRAC | maestra §5 |
| K | COD CARGO | maestra §5 |
| M | FEC INIC MONIT | `FechaInicio` |
| O | FEC FIN MONIT | `FechaFin` |
| Q | Dosis [mSv] | valor a 2 decimales; `0.00` si hay sigla |
| S | CANT | siempre `1` |
| U | OBSERVA | mapeo §7 |

### Hoja `REVISION`
Las inconsistencias de la Etapa 1 (§3), una por fila, con motivo.

---

## 7. Mapeo de OBSERVA (siglas del informe → ISP)

El ISP acepta en `OBSERVA`: `NU`, `DD`, `NR`, `<LD` o vacío.

| Informe (origen) | ISP (OBSERVA) | Dosis [mSv] |
|---|---|---|
| valor numérico | *(vacío)* | el valor (2 decimales) |
| `MNR` | `<LD` | 0.00 |
| `DSU` | `NU` | 0.00 |
| `DND`, `DD`, `DE`, `NR` | `NR` | 0.00 |

---

## 8. Modelo de datos (tablas `isp_*`)

Módulo **autocontenido**: las tablas `isp_*` no tienen relación (FK) con el resto
del sistema. La **empresa** (laboratorio: `Dosimet` | `Photomat`) se guarda como
texto propio en cada tabla y su lista la provee el propio módulo
(`GET /api/isp/empresas`), no la tabla `empresa` del sistema.

| Tabla | Contenido | Semilla en git |
|---|---|---|
| `isp_codigo_servicio` | Códigos RND (empresa, tecnología, magnitud, periodicidad → código) | ✅ Flyway V6 |
| `isp_clasificador` | Catálogos ISP (cargo, práctica, localización, sector): código + nombre | ✅ Flyway V6 |
| `isp_persona_codigo` | Maestra `RUT → cod_cargo, cod_prac` | ❌ se importa en la app |
| `isp_cliente_tecnologia` | Maestra `(empresa, RUT entidad) → tecnología` | ❌ se importa en la app |

---

## 9. Alcance del MVP y pendientes

- **MVP:** replicar el llenado de `COD SERV`, `COD PRAC`, `COD CARGO` y la generación
  del Excel ISP para Dosimet y Photomat, aprendiendo las maestras de un Q1.
- **Fuera del MVP:** los demás clasificadores del ISP (sector, etc.) hasta que la
  autoridad precise cómo pide llenarlos; el caso `BIMESTRAL` puntual se revisa aparte.
- **Pendiente de la operación:** marcar dedo/muñeca en `Ubicacion` antes de procesar;
  a futuro, un campo de tecnología en el informe crudo evitaría la maestra de tecnología.
