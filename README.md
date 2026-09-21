# Kissan Voice Platform

An event-driven backend for collecting a spoken-Urdu agricultural corpus:
field contributors are served questions, record answers on a phone, and the
platform stores the media, tracks corpus coverage, and **syncs contributor
activity into downstream sales/CRM systems** over an event stream and a set of
reusable integration building blocks.

> Java 21 · Spring Boot · PostgreSQL · Kafka · AWS S3 · n8n · Docker

See **[docs/ROADMAP.md](docs/ROADMAP.md)** for the architecture and the build plan.

## Status

| Branch | What is on it |
|---|---|
| `main` | The Java platform |
| `feat/spring-boot-platform` | Active development |
| `legacy/django-prototype` | The original Django/pandas prototype, kept for reference |

## Quick start

```bash
docker compose -f infra/docker-compose.yml up -d   # postgres, redpanda, localstack, wiremock, n8n
./mvnw spring-boot:run
open http://localhost:8080/swagger-ui.html
```
