# AGENTS.md

## Cursor Cloud specific instructions

### Project overview
MapTrace (地图时迹) is a monorepo with three components:
- **server/** — Spring Boot 3.4 backend (Java 17, Maven, MySQL 8, Redis 7)
- **admin-web/** — React + TypeScript + Vite admin console
- **miniprogram/** — WeChat Mini Program (requires WeChat DevTools; not runnable in cloud VM)

### Starting services

**Prerequisites (already installed by update script):**
- Java 17, Maven, MySQL 8, Redis 7, Node.js 18+

**1. Start MySQL and Redis:**
```
sudo mysqld --user=mysql --datadir=/var/lib/mysql &
sudo redis-server --daemonize yes
```

**2. Initialize database (first time only):**
```
sudo mysql < server/src/main/resources/db/init.sql
sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;"
```

**3. Create `server/src/main/resources/application-local.yml`:**
The default Spring profile is `local` (`${SPRING_PROFILE:local}`), but no `application-local.yml` is committed (gitignored). You must create one with MySQL (root/root), Redis (localhost:6379), and dummy values for COS/WeChat/Map keys. See `application-prod.yml` for the full structure.

**4. Build and run backend:**
```
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
cd server && mvn clean package -DskipTests
java -jar target/maptrace-server-0.0.1-SNAPSHOT.jar &
```
Backend serves on port 8080. Health check: `curl http://localhost:8080/actuator/health`

**5. Create `admin-web/.env.development`:**
```
VITE_API_BASE=
```
This makes the frontend use relative `/api/...` URLs, which Vite proxies to the backend. Without this, CORS blocks the browser from reading API responses.

**6. Run admin-web dev server:**
```
cd admin-web && npm install && npx vite --host 0.0.0.0
```
Frontend serves on port 5173. The Vite config includes a proxy from `/api` → `http://localhost:8080`.

### Key gotchas

- **CORS**: The frontend MUST use the Vite proxy (empty `VITE_API_BASE`) in dev mode. Direct cross-origin requests from `:5173` to `:8080` will fail silently — the backend processes the request (login succeeds in DB), but the browser blocks JS from reading the response.
- **Java version**: Must use Java 17 (not 21). Set `JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64`.
- **Default admin**: Username `admin`, password `Admin@2026`. Created automatically on first startup by `AdminAccountInitializer`. The `mustChangePassword` flag is true by default.
- **No automated tests**: The `server/` has no test source files. `mvn test` passes trivially.
- **No ESLint config**: `admin-web/` has no eslint config. Use `npx tsc -b` for type checking.
- **Lint/build commands**: `cd admin-web && npx tsc -b` (type check), `cd admin-web && npm run build` (production build), `cd server && mvn clean package -DskipTests` (backend build).
- **WeChat Mini Program**: Cannot be tested in cloud VM; requires WeChat Developer Tools desktop IDE.
