# Sistema de Gestión de Dosímetros

> Aplicación web para administrar el inventario, armado, asignación y trazabilidad
> de dosímetros de una empresa de dosimetría, reemplazando el proceso manual en Excel.

<p align="left">
  <img alt="Backend" src="https://img.shields.io/badge/Backend-Spring%20Boot%203-6DB33F?logo=springboot&logoColor=white">
  <img alt="Frontend" src="https://img.shields.io/badge/Frontend-React%2019%20%2B%20Vite-61DAFB?logo=react&logoColor=black">
  <img alt="Java" src="https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white">
  <img alt="DB" src="https://img.shields.io/badge/DB-MySQL%208-4479A1?logo=mysql&logoColor=white">
  <img alt="Tests" src="https://img.shields.io/badge/Tests-60%20passing-brightgreen">
</p>

---

## Tabla de contenido

- [¿De qué trata el proyecto?](#de-qué-trata-el-proyecto)
- [Características principales](#características-principales)
- [Manual de uso (qué hace, por rol)](#manual-de-uso-qué-hace-por-rol)
- [Módulos de la aplicación](#módulos-de-la-aplicación)
- [Roles y permisos](#roles-y-permisos)
- [Seguridad](#seguridad)
- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Requisitos](#requisitos)
- [Puesta en marcha (desarrollo)](#puesta-en-marcha-desarrollo)
- [Variables de entorno](#variables-de-entorno)
- [Base de datos y migraciones](#base-de-datos-y-migraciones)
- [Tests](#tests)
- [Documentación](#documentación)

---

## ¿De qué trata el proyecto?

Los **dosímetros** son dispositivos que miden la radiación a la que se expone una
persona. La empresa administra un gran inventario de dosímetros que se **asignan a
clientes** de sectores como salud, minería y construcción, se **reutilizan** entre
trimestres y se gestionan bajo distintas marcas comerciales.

Hasta ahora todo se llevaba en una planilla Excel de cientos de miles de filas:
lenta, difícil de consultar y propensa a errores. Este proyecto la reemplaza por una
**aplicación web** que centraliza y ordena todo el proceso:

- **Inventario y armado** de dosímetros (por tipo: OSL, TLD, Cristal, y su tipo de
  porta), organizados en tareas / bandejas / slots.
- **Asignación masiva** a clientes (cientos o miles a la vez) con validación de
  compatibilidad y de stock disponible.
- **Reutilización y trazabilidad**: cada dosímetro conserva su historial completo de
  asignaciones y ubicaciones.
- **Consulta por rol**: administradores y operadores gestionan todo; cada ejecutivo
  ve solo la información de sus clientes.
- **Dashboard** con indicadores y gráficos para la toma de decisiones.

## Características principales

- 📥 **Carga masiva** de dosímetros desde Excel, con plantilla descargable que incluye
  **listas desplegables** para tipo de dosímetro y tipo de porta.
- ✅ **Validación estricta ("todo o nada")**: si el archivo tiene filas erróneas, no se
  guarda nada y se muestra el detalle de cada error para corregirlo.
- 🧩 **Armado** por rango de bandeja/slot o slot a slot, incluida la opción de
  **des-armar** ("Sin armar") un rango.
- 🎯 **Asignación** masiva, individual y por archivo, con resumen exportable.
- ♻️ **Reutilización**: un dosímetro que vuelve de un cliente se re-arma y queda
  disponible; el historial de asignaciones se conserva.
- ⚠️ **Detección de duplicados físicos**: si un número ya existe armado en otra tarea,
  se avisa y se gestiona en el módulo Duplicados.
- 🔎 **Búsqueda** por número con historial completo y **correcciones masivas** (campo a
  campo o **editando un Excel de ida y vuelta**).
- 📊 **Dashboard** con KPIs, filtros reactivos por trimestre y detalle interactivo.
- 📈 **Pendiente de asignación**: compara un trimestre base con el actual para ver qué
  clientes continúan y cuáles faltan, ordenado por cantidad.
- 🧮 **Comparador de listados** (perfil ejecutivo): contrasta el listado del software con
  el del cliente y devuelve un Excel con los resultados.
- 📤 **Exportaciones a Excel/CSV** de stock, asignaciones, dosímetros y clientes.
- 🔐 **Seguridad de sesión**: la sesión se cierra al cerrar el navegador y por
  inactividad (30 min).
- 👥 Gestión unificada de **usuarios y ejecutivos**, clientes y tipos de porta.

---

## Manual de uso (qué hace, por rol)

Guía rápida de qué hace cada pantalla. Para el detalle paso a paso, ver
[`docs/MANUAL_USUARIO.md`](docs/MANUAL_USUARIO.md).

### 👑 Administrador
Acceso total. Además de todo lo del operador, gestiona:

- **Dashboard** — Indicadores del negocio (stock, armados, asignados, pendientes) con
  **gráficos** y **filtro por trimestre** que actualiza todo. Incluye stock histórico.
- **Importar Excel** — Carga masiva inicial de dosímetros desde una plantilla. Valida
  todo el archivo antes de guardar (todo o nada) y reporta cada error por fila.
- **Usuarios y ejecutivos** — Módulo con dos pestañas:
  - *Usuarios de acceso*: crea las cuentas de login y su **rol** (Administrador /
    Operador / Ejecutivo). Un usuario Ejecutivo se enlaza a un ejecutivo comercial.
  - *Ejecutivos*: alta y baja de los comerciales que tienen clientes asignados.

### 🛠️ Operador (y todo lo anterior para el Admin)
Opera el día a día del inventario:

- **Stock** — Inventario en árbol (**tipo → porta → tarea → bandeja → slot**). Carga por
  archivo (con desplegables), exportación, y detección de duplicados físicos.
- **Armar** — Asigna el tipo de porta a los dosímetros de una tarea. **Armado rápido**
  por rango de bandejas (incluye la opción **"Sin armar"** para des-armar) o **slot a
  slot** en la grilla de la bandeja.
- **Asignar** — Asigna dosímetros a clientes: **masiva** (por tareas), **individual**
  (por número) o **por archivo** (con plantilla y validación). Resumen exportable.
- **Dosímetros** — Busca por número, ve el **historial** completo de cada uno, edita sus
  especificaciones y hace **correcciones masivas** de asignaciones:
  - Corregir un mismo campo a varias seleccionadas (cliente, ejecutivo, empresa, porta,
    trimestre, bandeja, slot, fecha, link).
  - **Editar por Excel**: descarga las asignaciones, edítalas en Excel y vuelve a
    subirlas para aplicar los cambios (todo o nada).
- **Pendiente de asignación** — Compara clientes entre trimestres: elige un **trimestre
  base** y el actual, y ve quién continúa y quién quedó pendiente, ordenado por cantidad.
  Exporta la lista con los filtros aplicados.
- **Clientes** — Alta y gestión de clientes, con filtros y buscador.
- **Duplicados** — Números repetidos en el sistema; permite sacar uno de los dos y
  dejarlo de respaldo.
- **Tipos de porta** — Catálogo de portas por tipo de dosímetro.

### 👤 Ejecutivo (comercial)
Solo consulta lo suyo, sin poder modificar el inventario:

- **Mis dosímetros** — Sus asignaciones filtrando por **cliente → trimestre → fecha**
  (sin vista general: primero se filtra). Agrupa por fecha en **lotes** y permite
  **descargar** un lote puntual o todos juntos (orden fecha/tarea/bandeja/slot).
- **Mis clientes** — Sus clientes, con buscador y filtro de "pendientes de asignación".
- **Pendiente de asignación** — La misma comparación por trimestres, acotada a sus
  clientes.
- **Comparador** — Sube el listado del **software** y el del **cliente** y descarga un
  Excel con la comparación (mantener / quitar / nuevo / verificar). No guarda datos.

---

## Módulos de la aplicación

| Módulo | Descripción | Roles |
|---|---|---|
| **Dashboard** | KPIs, gráficos y stock histórico, reactivos al trimestre. | Administrador |
| **Stock** | Inventario jerárquico (tipo → porta → tarea → bandeja → slot), carga y exportación. | Admin · Operador |
| **Armar** | Asigna el tipo de porta de una tarea (rápido por rango, "Sin armar", o slot a slot). | Admin · Operador |
| **Asignar** | Asignación masiva / individual / por archivo, con resumen exportable. | Admin · Operador |
| **Dosímetros** | Búsqueda, historial, correcciones masivas (campo a campo y por Excel). | Admin · Operador |
| **Pendiente de asignación** | Comparación por trimestre base vs. actual, ordenada por cantidad. | Admin · Operador · Ejecutivo |
| **Clientes** | Alta y gestión de clientes con filtros. | Admin · Operador |
| **Duplicados** | Números repetidos en el sistema; permite sacar/backup uno de los dos. | Admin · Operador |
| **Tipos de porta** | Catálogo de portas por tipo de dosímetro. | Admin · Operador |
| **Importar Excel** | Carga masiva inicial de dosímetros. | Administrador |
| **Informe ISP** | Genera el informe dosimétrico trimestral para el ISP (RND): asigna códigos y produce el Excel oficial. | Administrador |
| **Seguimiento Chilexpress** | Registra y actualiza las Órdenes de Transporte desde el export de Chilexpress; búsqueda por cliente y rango de fecha. Módulo autocontenido. | Admin (carga) · Admin/Ejecutivo (consulta) |
| **Usuarios y ejecutivos** | Módulo unificado: cuentas de acceso (login + rol) y comerciales. | Admin (usuarios) · Operador (ejecutivos) |
| **Mis dosímetros** | Consulta filtrable del ejecutivo, con lotes por fecha y export. | Ejecutivo |
| **Mis clientes** | Clientes del ejecutivo, con buscador y pendientes. | Ejecutivo |
| **Comparador** | Compara listado del software vs. del cliente y devuelve un Excel. | Ejecutivo |

## Roles y permisos

| Rol | Alcance |
|---|---|
| **Administrador** | Acceso total: catálogos, usuarios, stock, asignaciones y dashboard. |
| **Operador** | Armar y asignar dosímetros, gestionar clientes/ejecutivos/portas, ver todo. |
| **Ejecutivo** | Solo consulta de sus clientes y dosímetros asignados, y el comparador. |

El control de acceso se aplica en dos capas: **rutas protegidas** en el frontend y
**`@PreAuthorize`** por rol en cada endpoint del backend.

## Seguridad

- **Cifrado en tránsito**: todo el tráfico va por **HTTPS/TLS**.
- **Contraseñas** guardadas con **bcrypt** (nunca en texto plano).
- **Autenticación por JWT** con expiración y **límite de intentos de login**
  (anti fuerza bruta).
- **Autorización por rol** en cada endpoint (`@PreAuthorize`).
- **Sesión** en `sessionStorage`: se cierra al cerrar el navegador y por **inactividad
  (30 min)**.
- **Base de datos no expuesta** a internet (solo accesible entre contenedores).
- **Secretos fuera del código** (variables de entorno; `.env` no se versiona).

## Arquitectura

```mermaid
flowchart LR
    U[Navegador] -->|HTTPS| C[Caddy]
    C -->|estáticos| SPA[React + Vite build]
    C -->|/api| API[Spring Boot API REST]
    API -->|JWT / Spring Security| API
    API -->|JPA / Hibernate| DB[(MySQL 8)]
    API -->|Apache POI| XLS[Excel: import/export]
    FW[Flyway] -->|migraciones| DB
```

- **SPA React** servida como estáticos; consume la API vía `axios` con token **JWT**.
- **API Spring Boot** stateless; autenticación por JWT y autorización por rol.
- **Caddy** como servidor web: sirve el frontend, reenvía `/api` al backend y gestiona
  el **HTTPS automático** (Let's Encrypt).
- **MySQL 8** en producción; el esquema y los datos base los versiona **Flyway**.
- **H2 en memoria** para los tests (no requiere MySQL).

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Frontend | React 19 · Vite · Tailwind CSS · Recharts · React Router · Axios |
| Backend | Java 17 · Spring Boot 3 (Web, Data JPA, Security) |
| Seguridad | Spring Security + JWT · rate limiting de login |
| Datos | Hibernate · MySQL 8 (prod) · H2 (tests) · Flyway |
| Excel | Apache POI · Apache Commons Text (comparador) |
| Despliegue | Docker Compose · Caddy (HTTPS automático) |
| Build | Maven (`mvnw`) · npm |

## Estructura del repositorio

```
dosimetros-sistema/
├── backend/          # API REST (Spring Boot) + migraciones Flyway + tests
│   ├── src/main/java/com/dosimetros/backend/
│   │   ├── controller/   # endpoints REST
│   │   ├── service/      # lógica de negocio
│   │   ├── repository/   # acceso a datos (Spring Data JPA)
│   │   ├── entity/       # entidades JPA
│   │   ├── dto/          # objetos de transferencia
│   │   ├── security/     # JWT, filtros, rate limiter
│   │   └── comparador/   # comparador de listados (servicios + modelos)
│   ├── Dockerfile
│   └── src/main/resources/db/migration/   # V1..V5 (Flyway)
├── frontend/         # SPA (React + Vite)
│   └── src/{pages,components,api,auth}/
├── migracion/        # utilidad Python para migrar el histórico desde Excel
├── deploy/           # Docker Compose, Caddy, backup y guía de despliegue
└── docs/             # manual de usuario
```

## Requisitos

- **Java 17+** (el wrapper `mvnw` ya viene incluido)
- **Node 20+**
- **MySQL 8** (solo para ejecutar; los tests usan H2)

## Puesta en marcha (desarrollo)

Con MySQL en ejecución:

```bash
# Backend  → http://localhost:8080
cd backend && ./mvnw spring-boot:run

# Frontend → http://localhost:5173
cd frontend && npm install && npm run dev
```

Al primer arranque, **Flyway** crea el esquema y carga los catálogos base y un usuario
administrador inicial. **Las credenciales de acceso se entregan de forma interna y
deben cambiarse tras el primer ingreso** (ver [`deploy/SEGURIDAD.md`](deploy/SEGURIDAD.md)).

> Para desplegar en un servidor con Docker y HTTPS automático, ver la guía
> [`deploy/DESPLIEGUE.md`](deploy/DESPLIEGUE.md).

## Variables de entorno

La configuración (conexión a BD, secretos, orígenes CORS) se toma de **variables de
entorno**; en desarrollo hay valores por defecto. Plantilla completa en
[`backend/.env.example`](backend/.env.example):

| Variable | Descripción |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a MySQL. |
| `JWT_SECRET`, `JWT_EXPIRATION_MS` | Secreto (≥32 chars) y expiración del token. |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend permitidos (coma-separados). |
| `JPA_SHOW_SQL`, `SECURITY_LOG_LEVEL` | Observabilidad / debug. |

## Base de datos y migraciones

El esquema y los datos base los gestiona **Flyway**
(`backend/src/main/resources/db/migration`, versiones `V1`–`V5`). Cada cambio de
esquema se agrega como una nueva migración versionada. Para migrar el histórico real
desde el Excel, ver [`migracion/README.md`](migracion/README.md).

## Tests

```bash
cd backend && ./mvnw test
```

**48 pruebas** que cubren la lógica crítica (importación/actualización de stock,
asignaciones, reglas de duplicados, seguridad de login) sobre **H2** — no requieren
MySQL.

## Documentación

- 📘 **Manual de usuario** (por módulo y por rol): [`docs/MANUAL_USUARIO.md`](docs/MANUAL_USUARIO.md).
- 🧾 **Módulo Informe ISP (RND)** — especificación funcional, reglas y mapeos: [`docs/INFORME_ISP.md`](docs/INFORME_ISP.md).
- 🚀 **Guía de despliegue** (Docker + HTTPS): [`deploy/DESPLIEGUE.md`](deploy/DESPLIEGUE.md).
- 🔒 **Checklist de seguridad**: [`deploy/SEGURIDAD.md`](deploy/SEGURIDAD.md).

---

<sub>Proyecto interno de gestión de dosímetros · Dosimet.</sub>
