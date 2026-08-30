# Guía de despliegue (VPS + Docker + HTTPS gratis)

Despliega el sistema completo (frontend + backend + MySQL) en **una sola máquina**
con **HTTPS automático** usando un **subdominio gratuito** (DuckDNS). Pensado para uso
interno con pocos usuarios.

Arquitectura resultante:

```
Internet ──HTTPS──> Caddy ─┬─ sirve el frontend (React)
                           └─ /api ─> Backend (Spring Boot) ─> MySQL
```

Todo corre con **Docker Compose** en tres contenedores (`web`, `backend`, `db`).

---

## 1. Requisitos

- Una **VPS** con Ubuntu 22.04+ (AWS Lightsail 2 GB, Hetzner CX22, DigitalOcean, etc.).
- Los puertos **80** y **443** abiertos hacia internet (y **22** para SSH).
- Acceso SSH a la máquina.

## 2. Subdominio gratis (DuckDNS)

1. Entra a <https://www.duckdns.org> e inicia sesión (Google/GitHub).
2. Crea un subdominio, por ejemplo `dosimet` → te queda `dosimet.duckdns.org`.
3. En el campo **current ip** pon la **IP pública de tu VPS** y guarda.
   (Si la IP cambia, actualízala ahí; en Lightsail/Hetzner suele ser fija.)

> Alternativa: si compras un dominio propio (`.cl`/`.com`), solo crea un registro
> **A** que apunte a la IP de la VPS y usa ese nombre en `DOMAIN`.

## 3. Instalar Docker en la VPS

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"    # cierra sesión y vuelve a entrar
```

## 4. Clonar el proyecto y configurar

```bash
git clone https://github.com/valdasy/dosimetros-sistema.git
cd dosimetros-sistema/deploy

cp .env.example .env
nano .env        # completa DOMAIN, DB_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET
```

Genera un `JWT_SECRET` seguro con:

```bash
openssl rand -base64 48
```

## 5. Levantar todo

```bash
docker compose up -d --build
```

La primera vez compila el backend y el frontend (unos minutos). Luego:

- Caddy pide el certificado HTTPS a Let's Encrypt automáticamente.
- Flyway crea el esquema y carga los catálogos base y el usuario administrador.

Abre **`https://TU-SUBDOMINIO.duckdns.org`** en el navegador. 🎉

Ver estado y logs:

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f web
```

## 6. Crear los usuarios

Ingresa con el administrador inicial (las credenciales se entregan de forma interna;
**cámbialas al primer ingreso**) y en el módulo **"Usuarios y ejecutivos"** crea las
cuentas que necesites con su rol (Administrador / Operador / Ejecutivo).

## 7. Backups de la base de datos

Prueba manual:

```bash
./backup-db.sh
```

Automatiza uno diario a las 03:00 con cron:

```bash
crontab -e
# añade esta línea (ajusta la ruta):
0 3 * * * /ruta/a/dosimetros-sistema/deploy/backup-db.sh >> /var/log/dosimetros-backup.log 2>&1
```

Guarda una copia fuera del servidor cada cierto tiempo (descárgalas por `scp`).

## 8. Actualizar a una nueva versión

```bash
cd dosimetros-sistema
git pull
cd deploy
docker compose up -d --build
```

---

## Seguridad (revisar sí o sí)

- **Cambia la contraseña del administrador** al primer ingreso.
- `JWT_SECRET` largo y aleatorio (ya en `.env`, nunca lo subas al repo).
- **Firewall**: expón solo 80/443 y 22. **MySQL nunca** debe quedar abierto a internet
  (en esta configuración solo es accesible entre contenedores, no desde afuera).
- Mantén el sistema operativo actualizado (`sudo apt update && sudo apt upgrade`).
- Ver también la checklist [`SEGURIDAD.md`](SEGURIDAD.md).

## Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El navegador no carga / sin candado | 80/443 cerrados o DNS mal | Abre los puertos y verifica que el subdominio apunte a la IP correcta. |
| `backend` reinicia en bucle | No conecta a MySQL | Revisa `DB_PASSWORD` en `.env` y `docker compose logs backend`. |
| Certificado no se emite | Puerto 80 bloqueado | Let's Encrypt necesita el 80 accesible; ábrelo. |
