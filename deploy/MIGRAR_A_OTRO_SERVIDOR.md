# Migrar el sistema a otro servidor (conservando los datos)

Guía para un informático que deba **levantar este sistema en un servidor nuevo**
(otra VPS / máquina virtual) **con los datos actuales**.

La idea de fondo: al servidor nuevo hay que llevarle **dos cosas**:

1. **El código** — está en GitHub (`valdasy/dosimetros-sistema`).
2. **Un respaldo de la base de datos actual** — un dump de MySQL.

El **esquema** de la base lo crea Flyway automáticamente al arrancar; los
**datos** se copian con el respaldo. Todo corre con Docker Compose (contenedores
`web` + `backend` + `db`), igual que el servidor original. La guía de instalación
desde cero está en [`DESPLIEGUE.md`](DESPLIEGUE.md); este documento añade la parte
de **copiar los datos**.

---

## Requisitos del servidor nuevo

- **Ubuntu 22.04+** (2 GB de RAM o más).
- Puertos **80** y **443** abiertos a internet, y **22** para SSH.
- Un **dominio o subdominio** apuntando (por DNS) a la **IP pública del servidor
  nuevo** — necesario para el certificado HTTPS. (Ej. gratis en DuckDNS, o un
  registro A si es dominio propio.)
- Acceso al repositorio `valdasy/dosimetros-sistema`.

---

## Paso 1 — Respaldar la base en el servidor ACTUAL

En el servidor **actual**, dentro de `~/dosimetros-sistema/deploy`:

```bash
export $(grep -E '^DB_ROOT_PASSWORD=' .env | xargs)
docker compose exec -T db mysqldump -uroot -p"$DB_ROOT_PASSWORD" \
  --single-transaction --routines --triggers \
  dosimetros_db > respaldo_dosimetros.sql
```

Esto genera `respaldo_dosimetros.sql` con **todo**: esquema, datos e historial de
migraciones (`flyway_schema_history`). Hazlo idealmente con nadie usando el
sistema para tener una foto consistente.

Copia ese archivo al servidor nuevo (por ejemplo con `scp`).

---

## Paso 2 — Preparar el servidor NUEVO

```bash
# Instalar Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"      # cerrar sesión y volver a entrar

# Traer el código
git clone https://github.com/valdasy/dosimetros-sistema.git
cd dosimetros-sistema/deploy

# Configurar variables de entorno
cp .env.example .env
nano .env
```

En `.env` completa:

- `DOMAIN` → el dominio/subdominio del **servidor nuevo**.
- `DB_PASSWORD` y `DB_ROOT_PASSWORD` → claves fuertes (pueden ser distintas a las
  del servidor original; el respaldo no incluye usuarios de MySQL, solo la base
  `dosimetros_db`).
- `JWT_SECRET` → uno propio y largo. Genera con: `openssl rand -base64 48`.

---

## Paso 3 — Levantar SOLO la base y restaurar los datos

Primero arranca únicamente el contenedor de la base (vacío) y restaura el
respaldo **antes** de levantar el backend:

```bash
docker compose up -d db

# Espera ~20 s a que MySQL quede "healthy":
docker compose ps

# Restaurar el respaldo (ajusta la ruta del archivo si es necesario)
export $(grep -E '^DB_ROOT_PASSWORD=' .env | xargs)
docker compose exec -T db mysql -uroot -p"$DB_ROOT_PASSWORD" dosimetros_db < respaldo_dosimetros.sql
```

---

## Paso 4 — Levantar el resto del sistema

```bash
docker compose up -d --build
docker compose logs -f backend
```

En el log del backend, Flyway verá que el historial ya está en la última versión
(gracias a `flyway_schema_history` del respaldo) y **no re-ejecutará** las
migraciones. Debe terminar en `Started BackendApplication in X seconds`.
Sal del log con **Ctrl+C**.

---

## Paso 5 — Verificar

Conteos de datos (deben coincidir con el servidor original):

```bash
export $(grep -E '^DB_ROOT_PASSWORD=' .env | xargs)
docker compose exec -T db mysql -uroot -p"$DB_ROOT_PASSWORD" dosimetros_db \
  -e "SELECT (SELECT COUNT(*) FROM dosimetro) dosim, (SELECT COUNT(*) FROM cliente) cli, (SELECT COUNT(*) FROM ejecutivo) ej, (SELECT COUNT(*) FROM ASIGNACION) asig;"
```

Luego abre `https://EL-DOMINIO-NUEVO` en el navegador e ingresa. Confirma que se
ve el histórico.

---

## Notas importantes

- **No borres la tabla `flyway_schema_history`** del respaldo: es la que evita que
  Flyway vuelva a correr las migraciones sobre datos ya cargados.
- **`lower-case-table-names=1`** ya viene fijado en `docker-compose.yml` (por la
  tabla `ASIGNACION`, en mayúsculas). No lo cambies: debe fijarse en la **primera**
  inicialización de la base, que es justo lo que ocurre en el Paso 3.
- **Versión de MySQL**: se usa la imagen `mysql:8`; mantén MySQL 8 en el servidor
  nuevo (el compose ya lo hace).
- **Seguridad**: usa un `JWT_SECRET` propio, cambia la contraseña del
  administrador tras el primer ingreso, y deja el firewall con solo 80/443/22
  abiertos. MySQL **nunca** debe quedar expuesto a internet (en esta
  configuración solo es accesible entre contenedores).
- **DNS antes de levantar**: el dominio debe apuntar a la IP nueva **antes** del
  Paso 4, para que Let's Encrypt pueda emitir el certificado HTTPS.

---

## Alternativa (si el informático prefiere copiar el volumen completo)

En vez del dump lógico, se puede copiar el volumen Docker de MySQL
(`dosimetros_db-data`) del servidor viejo al nuevo. Es más rápido para bases muy
grandes, pero requiere **misma versión de MySQL** y detener el contenedor `db` en
ambos extremos. El método del **respaldo SQL (Pasos 1–4) es el recomendado** por
ser portable y no depender de la versión exacta del motor.
