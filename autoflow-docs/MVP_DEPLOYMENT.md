# AutoFlow AI — MVP Cloud Infrastructure Runbook

## Why the Cost-Aware Modular Monolith Architecture?

AutoFlow AI intentionally utilizes a **Modular Monolith** deployment strategy for the MVP phase:

1. **Capital Efficiency & Low Burn**: Running microservices in a multi-node Kubernetes cluster (EKS/GKE) costs upwards of \$800–\$2,500/month in idle node pools, NAT gateways, load balancers, and monitoring before achieving product-market fit. A modular monolith runs on a single high-performance VM (Hetzner CPX31 / 4 vCPU, 8 GB RAM, or AWS EC2 `t4g.xlarge`) for **\$20–\$40/month**, delivering 99.9% uptime and sub-15ms internal latency.
2. **Zero Network Latency**: In-process domain calls between Billing, CRM, Webhooks, and Social Accounts run via direct method invocations without inter-service HTTP/gRPC network overhead or serialization lag.
3. **Transactional Integrity**: Database operations across ledger balances, subscriptions, and quotas execute inside ACID PostgreSQL transactions without complex distributed 2PC (Two-Phase Commit) or Saga coordinators.
4. **Virtual Threads (Loom)**: Java 21 handles millions of concurrent I/O requests on minimal hardware with zero thread exhaustion.

---

## Single-VM Production Topology

```
 Internet (HTTPS 443 / HTTP 80)
               │
               ▼
   ┌───────────────────────┐
   │        Caddy 2        │  <-- Automated Let's Encrypt SSL & Compression
   └───────────┬───────────┘
               │
       ┌───────┴────────┐
       ▼                ▼
┌──────────────┐ ┌──────────────┐
│ Flutter Web  │ │ Spring Boot  │  <-- Modular Monolith (Java 21 Virtual Threads)
│ Nginx Static │ │ Port 8080    │
└──────────────┘ └──────┬───────┘
                        │
         ┌──────────────┼──────────────┐
         ▼              ▼              ▼
  ┌──────────────┐ ┌──────────┐ ┌──────────────┐
  │PostgreSQL 16 │ │ Redis 7  │ │  MinIO / S3  │
  │  + pgvector  │ │ AOF + Auth│ │ Media Storage│
  └──────────────┘ └──────────┘ └──────────────┘
```

---

## Deployment Steps

### 1. Provision Server
* Any Ubuntu 22.04 / Debian 12 cloud server (Hetzner Cloud CPX31, DigitalOcean, or AWS EC2).
* Install Docker and Docker Compose v2:
  ```bash
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker $USER
  ```

### 2. Clone Repository & Set Environment
```bash
git clone https://github.com/PRASANNAKUMAR8106/Automation_Ai.git /opt/autoflow
cd /opt/autoflow
cp .env.example .env.prod
# Fill in required production secrets in .env.prod
```

### 3. Launch Stack
```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

### 4. Database Automated Backup Script
Create `/opt/autoflow/scripts/backup.sh`:
```bash
#!/bin/bash
set -e
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="/opt/autoflow/backups/autoflow_db_${TIMESTAMP}.sql.gz"
mkdir -p /opt/autoflow/backups

docker exec autoflow_postgres_prod pg_dump -U autoflow_app autoflow_prod_db | gzip > "$BACKUP_FILE"
find /opt/autoflow/backups -type f -mtime +14 -delete
echo "Backup successful: $BACKUP_FILE"
```
Cron job (`crontab -e`):
```cron
0 2 * * * /opt/autoflow/scripts/backup.sh >> /var/log/autoflow_backup.log 2>&1
```
