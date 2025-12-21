# Repository Guidelines

## Project Structure & Module Organization

- `frontend/`: Vue 3 + Vite + TypeScript UI (routes, views, components, API clients in `frontend/src/`).
- `backend/`: Spring Boot REST API (source in `backend/src/main/java/com/etc/`, config in `backend/src/main/resources/application.yml`).
- `data-service/`: Python scripts/utilities for CSV import and realtime simulation (`data-service/scripts/`).
- `flink-jobs/`: Flink streaming jobs packaged as a shaded JAR (`flink-jobs/src/main/java/`).
- `infra/` + `docker-compose.yml`: local stack (MySQL/Redis/Kafka/Flink/etc.). Docs live in `doc/`.

## Build, Test, and Development Commands

- Full stack (Docker): `docker compose up -d --build`.
- Frontend:
  - Install/run: `cd frontend && pnpm install && pnpm dev` (http://localhost:5173)
  - Lint/build: `pnpm lint`, `pnpm build`
- Backend (requires JDK 17+ and Maven):
  - Run: `mvn -f backend/pom.xml spring-boot:run`
  - Package/Test: `mvn -f backend/pom.xml clean test` / `mvn -f backend/pom.xml clean package`
- Flink jobs (Java 11): `mvn -f flink-jobs/pom.xml clean package -DskipTests`
- Data service: `cd data-service && pip install -r requirements.txt`, then:
  - 历史数据入库（2023-12 → HBase）: `python -m scripts.import_to_hbase`
  - 实时数据模拟（2024-01 → Kafka）: `python -m scripts.realtime_simulator`
- Flink jobs（需要先编译 `flink-jobs/`，JAR 会挂载到 JobManager）:
  - 检查 JAR 是否挂载: `docker compose exec flink-jobmanager ls -la /opt/flink/jobs`
  - MySQL 热数据落库: `docker compose exec flink-jobmanager flink run -d -c com.etc.flink.MySqlStorageJob /opt/flink/jobs/etc-flink-jobs-1.0.0.jar`
  - HBase 归档落库: `docker compose exec flink-jobmanager flink run -d -c com.etc.flink.HBaseStorageJob /opt/flink/jobs/etc-flink-jobs-1.0.0.jar`

## Coding Style & Naming Conventions

- Frontend formatting is enforced via Prettier/ESLint/Stylelint (2-space indent; see `frontend/.prettierrc`). Prefer `PascalCase.vue` components and `useXxx` composables under `frontend/src/hooks/`.
- Backend follows standard Spring conventions: `Controller/Service/Repository` packages, `PascalCase` classes, and `camelCase` members.

## Testing Guidelines

- Backend uses Spring Boot’s test stack (JUnit). Add tests under `backend/src/test/java` and run `mvn -f backend/pom.xml test`.
- Frontend/data-service currently rely on linting and runtime checks; add targeted tests when introducing non-trivial logic.

## Commit & Pull Request Guidelines

- Prefer Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`).
- PRs should include: a clear description, key changes, and screenshots/GIFs for UI changes. Ensure lint/build commands for touched modules pass.

## Configuration & Security Tips

- Don’t commit secrets. Use `.env.*` in `frontend/` and environment variables for services (e.g., `MYSQL_HOST`, `REDIS_HOST` in `backend/`).
