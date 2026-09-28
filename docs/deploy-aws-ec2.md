# Deploying to AWS EC2

This guide deploys the ingestion service and the React frontend to one EC2 instance, with PostgreSQL on
RDS and uploaded files in S3.

```
Browser ──HTTPS──▶ EC2: nginx (80/443)
                     ├─ /            → React build (static files)
                     └─ /ingestion/  → Docker container :8080 (bound to 127.0.0.1 only)
                                          ├─▶ RDS PostgreSQL 16 (private, port 5432)
                                          └─▶ S3 bucket (access via the instance's IAM role)
```

nginx serves both the frontend and the API on the same origin, so the browser needs no CORS, and port
8080 is never exposed to the internet.

> **Before exposing this publicly:** the API has no authentication yet. `userId` (upload) and
> `managerUserId` (approve/reject) are taken from the request, so anyone who can reach the API can act
> as any user, including a manager. Until login is in place, restrict the security group to known IPs
> (office/VPN) instead of `0.0.0.0/0`.

Region used below: `ap-south-1` (the service's default). Substitute your own where different.

---

## 1. S3 bucket

1. S3 → **Create bucket**, name `mavhu-data-ingestion-engine-service` (or your own, then set `S3_BUCKET`
   in step 6), region `ap-south-1`.
2. Keep **Block all public access** on. Keep default encryption (SSE-S3).
3. Optional: enable **Versioning** so overwritten or deleted uploads can be recovered.

## 2. Security groups

Create two security groups in the VPC you'll use:

| Group | Inbound rule | Source |
|---|---|---|
| `mavhu-ec2-sg` | SSH 22 | your IP only |
| | HTTP 80, HTTPS 443 | your office/VPN IPs (or `0.0.0.0/0` once the API has auth) |
| `mavhu-rds-sg` | PostgreSQL 5432 | `mavhu-ec2-sg` (the security group, not an IP) |

Do **not** open port 8080.

## 3. RDS PostgreSQL

1. RDS → **Create database** → PostgreSQL 16, template *Production* or *Dev/Test*.
2. DB instance identifier e.g. `mavhu-db`; set the master username/password (store them safely).
3. Connectivity: same VPC as the EC2 instance, **Public access: No**, security group `mavhu-rds-sg`.
4. Additional configuration → initial database name `mavhu_db`.

The database schema is set up in step 7, after the EC2 instance exists (the database is only reachable
from inside the VPC).

## 4. IAM role for the instance

The app gets AWS credentials from the instance role, so no access keys are stored on the server.

1. IAM → **Policies** → **Create policy** → JSON:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "IngestionFiles",
         "Effect": "Allow",
         "Action": ["s3:PutObject", "s3:GetObject"],
         "Resource": "arn:aws:s3:::mavhu-data-ingestion-engine-service/*"
       }
     ]
   }
   ```

   Name it `mavhu-ingestion-s3`.
2. IAM → **Roles** → **Create role** → trusted entity *AWS service* / *EC2* → attach `mavhu-ingestion-s3`
   and `AmazonSSMManagedInstanceCore` (enables Session Manager as an alternative to SSH).
   Name it `mavhu-ingestion-ec2`.

## 5. Launch the EC2 instance

1. EC2 → **Launch instance**:
   - AMI: **Amazon Linux 2023**
   - Instance type: **t3.small** (2 GiB) at minimum; t3.medium if the frontend is also built on the box
   - Key pair: create or choose one
   - Network: same VPC as RDS, a public subnet, **auto-assign public IP**, security group `mavhu-ec2-sg`
   - Storage: 20 GiB gp3
   - Advanced details → IAM instance profile: `mavhu-ingestion-ec2`
2. Optional: allocate an **Elastic IP** and associate it, so the address survives stop/start.
3. Connect and install the tools:

   ```bash
   ssh -i <key>.pem ec2-user@<ec2-public-ip>

   sudo dnf update -y
   sudo dnf install -y docker git nginx postgresql16
   sudo systemctl enable --now docker nginx
   sudo usermod -aG docker ec2-user
   exit   # log out and back in so the docker group applies
   ```

4. Cap Docker log size so container logs can't fill the disk:

   ```bash
   sudo tee /etc/docker/daemon.json >/dev/null <<'EOF'
   { "log-driver": "json-file", "log-opts": { "max-size": "20m", "max-file": "5" } }
   EOF
   sudo systemctl restart docker
   ```

## 6. Configuration

Create an environment file readable only by root:

```bash
sudo mkdir -p /etc/mavhu
sudo tee /etc/mavhu/ingestion.env >/dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/mavhu_db
DB_USERNAME=mavhu_ingestion_service
DB_PASSWORD=<password>
AWS_REGION=ap-south-1
S3_BUCKET=mavhu-data-ingestion-engine-service
# Only needed if the frontend is served from a different origin than the API (see step 9)
CORS_ALLOWED_ORIGINS=https://esg.example.com
JAVA_OPTS=-XX:MaxRAMPercentage=75.0
EOF
sudo chmod 600 /etc/mavhu/ingestion.env
```

- **Do not** set `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`: the SDK picks up the instance role.
- For stronger secret handling, keep `DB_PASSWORD` in SSM Parameter Store / Secrets Manager and write
  this file from it during deploy.

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (Hibernate validates the schema on startup) or `staging` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | RDS connection |
| `AWS_REGION` | Region of the bucket |
| `S3_BUCKET` | Upload bucket (default `mavhu-data-ingestion-engine-service`) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API |
| `MAX_FILE_SIZE`, `MAX_REQUEST_SIZE` | Upload limit (default `50MB`) |
| `FILE_PROCESSOR_BASE_URL` | Leave unset; the upload API calls the processor on the same instance |
| `JAVA_OPTS` | JVM options; heap is sized from the container's memory |

## 7. Database schema

The `prod` profile validates the schema on startup, so the database must be ready **before** the app
starts. Run these from the EC2 instance (it can reach RDS; your laptop can't).

1. **Platform migrations first.** The `mavhu` schema, the `customers` / `users` / `roles` / `banks`
   tables and the `mavhu_ingestion_service` and `mavhu_admin` database roles come from the platform
   migrations (`schema_migrations` 001–003), not this repo. Apply them to `mavhu_db` as the master user.
2. **This service's DDL**, as `mavhu_ingestion_service`, in this order:

   ```bash
   git clone <repo-url> ~/mavhu-data-ingestion-engine-service
   cd ~/mavhu-data-ingestion-engine-service
   export PGPASSWORD='<mavhu_ingestion_service password>'
   RDS=<rds-endpoint>

   psql -h $RDS -U mavhu_ingestion_service -d mavhu_db -v ON_ERROR_STOP=1 -f src/main/resources/db/ddl/esg_file_ingestion.sql
   psql -h $RDS -U mavhu_ingestion_service -d mavhu_db -v ON_ERROR_STOP=1 -f src/main/resources/db/ddl/esg_file_ingestion_grants.sql
   psql -h $RDS -U mavhu_ingestion_service -d mavhu_db -v ON_ERROR_STOP=1 -f src/main/resources/db/ddl/esg_file_approval.sql
   ```

   All three are idempotent, so re-running them is safe.
3. **Mock data: dev/staging only, never production.**

   ```bash
   psql -h $RDS -U mavhu_ingestion_service -d mavhu_db -v ON_ERROR_STOP=1 -f src/main/resources/db/mock/cbz_mock_data.sql
   ```

   It prints the generated customer and user IDs.

## 8. Build and run the service

```bash
cd ~/mavhu-data-ingestion-engine-service
docker build -t mavhu-ingestion:latest .

docker run -d --name mavhu-ingestion \
  --restart unless-stopped \
  --env-file /etc/mavhu/ingestion.env \
  -p 127.0.0.1:8080:8080 \
  mavhu-ingestion:latest

# Healthy within ~10s:
curl -s localhost:8080/actuator/health     # {"status":"UP"}
curl -s localhost:8080/ingestion/v1/alive  # alive
```

- `-p 127.0.0.1:8080:8080` publishes the port on localhost only; nginx is the only way in.
- `--restart unless-stopped` brings the container back after a reboot or crash.
- The first build downloads Gradle and dependencies (a few minutes). The image builds with the
  project's Gradle wrapper on Java 25 and runs as a non-root user.
- If the build runs out of memory on a t3.small, build on a bigger machine (or in CI), push the image to
  **ECR**, and `docker pull` it on the instance instead.

## 9. nginx: frontend + API on one origin

1. Build the React app so it calls the API with **relative** URLs (`/ingestion/v1/...`, no host), then
   copy the build output to the server:

   ```bash
   # on your machine
   npm run build
   scp -i <key>.pem -r dist/* ec2-user@<ec2-public-ip>:/tmp/esg-frontend/
   # on the server
   sudo mkdir -p /var/www/esg && sudo cp -r /tmp/esg-frontend/* /var/www/esg/
   ```

2. Create `/etc/nginx/conf.d/mavhu.conf`:

   ```nginx
   server {
       listen 80;
       server_name esg.example.com;   # or _ to match the IP

       # nginx rejects bodies over 1 MB by default; uploads are allowed up to 50 MB.
       client_max_body_size 50m;

       root /var/www/esg;
       index index.html;

       # React routes: unknown paths fall back to index.html
       location / {
           try_files $uri $uri/ /index.html;
       }

       location /ingestion/ {
           proxy_pass http://127.0.0.1:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           proxy_read_timeout 120s;
       }

       location = /actuator/health {
           proxy_pass http://127.0.0.1:8080;
       }
   }
   ```

3. Remove the default server block if it conflicts, test and reload:

   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   curl -s http://<ec2-public-ip>/actuator/health
   ```

Because the frontend and API share one origin through nginx, `CORS_ALLOWED_ORIGINS` isn't used by the
browser. Set it only if the frontend is served from a different origin (another port or domain).

## 10. HTTPS

With a domain pointing at the instance (Route 53 or your DNS: an A record to the Elastic IP):

```bash
sudo dnf install -y certbot python3-certbot-nginx
sudo certbot --nginx -d esg.example.com
```

certbot adds the 443 server block and a redirect from 80, and renews automatically. Then set
`CORS_ALLOWED_ORIGINS=https://esg.example.com` if you use it.

Alternative for production at scale: put an **Application Load Balancer** with an **ACM** certificate
in front and use `/actuator/health` as the target-group health check.

## 11. Smoke test

With the mock data loaded (dev/staging) and the IDs it printed (customer `1`, uploader user `3`,
manager user `1` on a fresh database):

```bash
BASE=https://esg.example.com

# Upload
curl -s -F "file=@src/main/resources/sample/03_Scope_1_Direct.xlsx" \
     -F "fileType=SCOPE_1_DIRECT" -F "userId=3" \
     $BASE/ingestion/v1/customers/1/files
# -> 201 with fileId, jobId, approvalStatus PENDING; the object is now in S3

# Check ingestion (repeat until ingestion.status is COMPLETED)
curl -s $BASE/ingestion/v1/customers/1/files/<fileId>

# Approve as a manager
curl -s -H 'Content-Type: application/json' \
     -d '{"managerUserId": 1, "comment": "Smoke test"}' \
     $BASE/ingestion/v1/customers/1/files/<fileId>/approve
```

Or run the whole Insomnia collection with `baseUrl` set to the server.

## 12. Redeploying

```bash
cd ~/mavhu-data-ingestion-engine-service
git pull
# Apply any new DDL under src/main/resources/db/ddl first (step 7) — prod validates the schema on startup.
docker build -t mavhu-ingestion:latest .
docker rm -f mavhu-ingestion
docker run -d --name mavhu-ingestion --restart unless-stopped \
  --env-file /etc/mavhu/ingestion.env -p 127.0.0.1:8080:8080 mavhu-ingestion:latest
curl -s localhost:8080/actuator/health
```

The service is down for the few seconds between `rm` and startup. Jobs that were mid-processing stay in
`PROCESSING`; re-trigger them via `POST /ingestion/v1/files/process` with the file's `s3Path`.

## 13. Operations

| Task | Command |
|---|---|
| App logs | `docker logs -f mavhu-ingestion` |
| nginx logs | `sudo tail -f /var/log/nginx/access.log /var/log/nginx/error.log` |
| Restart app | `docker restart mavhu-ingestion` |
| Container status | `docker ps` |
| Connect to DB | `psql -h <rds-endpoint> -U mavhu_ingestion_service -d mavhu_db` |

Recommended: enable RDS automated backups (default 7 days), and CloudWatch alarms on EC2 CPU/status
checks and RDS free storage.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `413 Request Entity Too Large` from nginx | `client_max_body_size` missing in the nginx config |
| Upload returns 500, logs show S3 `AccessDenied` | Instance role missing, or policy bucket name doesn't match `S3_BUCKET` |
| Container exits on start: `Schema-validation: missing table` | DDL not applied (step 7) before starting with the `prod` profile |
| `Connection refused` / timeout to RDS | `mavhu-rds-sg` doesn't allow 5432 from `mavhu-ec2-sg`, or different VPC |
| Upload `403 User … does not belong to customer` | `userId` isn't a user of that customer; check the IDs the mock script printed |
| Browser CORS error | Frontend isn't going through nginx; call relative `/ingestion/...` URLs, or add its origin to `CORS_ALLOWED_ORIGINS` |
| Jobs stuck in `PENDING` | Check `docker logs` for "Could not trigger processing"; leave `FILE_PROCESSOR_BASE_URL` unset |
