# mavhu-data-ingestion-engine-service

Spring Boot service using Java 25, with AWS S3 + SQS integration dependencies and PostgreSQL (RDS-compatible) datasource configuration.

## Features

- Java 25 + Spring Boot
- `GET /ingestion/v1/alive` liveness endpoint returning `alive`
- AWS dependencies:
  - S3 (`spring-cloud-aws-starter-s3`)
  - SQS (`spring-cloud-aws-starter-sqs`)
- PostgreSQL datasource for Amazon RDS-compatible setup
- Dockerized for EC2 deployment

## Run Locally

Requirements: Java 25 and Gradle.

Start PostgreSQL with default credentials (`postgres` / `postgres`):

```bash
docker compose up -d
```

Run the app with the `local` profile. On startup it connects to the default `postgres` database and creates `mavhu_local_db` if it does not exist.

```bash
SPRING_PROFILES_ACTIVE=local gradle bootRun
```

Test:

```bash
curl http://localhost:8080/ingestion/v1/alive
```

Expected response:

```text
alive
```

## Environment Variables

Set these values in EC2 (or locally) before running the container:

- `SERVER_PORT` (default `8080`)
- `DB_URL` (example `jdbc:postgresql://<rds-endpoint>:5432/<database>`)
- `DB_USERNAME`
- `DB_PASSWORD`
- `AWS_REGION` (example `ap-south-1`)
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

## Spring Profiles

Use one of these profiles at runtime:

- `local` (uses `application-local.yml`)
- `staging` (uses `application-staging.yml`)
- `prod` (uses `application-prod.yml`)

Example:

```bash
SPRING_PROFILES_ACTIVE=local gradle bootRun
```

## Docker Build and Run

Build:

```bash
docker build -t mavhu-data-ingestion-engine-service:latest .
```

Run:

```bash
docker run -d --name mavhu-data-ingestion-engine-service \
  -p 8080:8080 \
  -e SERVER_PORT=8080 \
  -e DB_URL=jdbc:postgresql://<rds-endpoint>:5432/<database> \
  -e DB_USERNAME=<db-username> \
  -e DB_PASSWORD=<db-password> \
  -e AWS_REGION=<aws-region> \
  -e AWS_ACCESS_KEY_ID=<aws-access-key-id> \
  -e AWS_SECRET_ACCESS_KEY=<aws-secret-access-key> \
  mavhu-data-ingestion-engine-service:latest
```

Verify:

```bash
curl http://<ec2-public-ip>:8080/ingestion/v1/alive
```

## EC2 Deployment

See [docs/deploy-aws-ec2.md](docs/deploy-aws-ec2.md) for the full guide: S3, RDS, IAM role, EC2 setup,
database DDL, Docker, nginx (frontend + API on one origin), HTTPS, redeploys and troubleshooting.
