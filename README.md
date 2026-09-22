# AutoFlow AI — Production SaaS Platform

AutoFlow AI is a high-throughput, cross-platform social-media automation and marketing engine for creators, coaches, agencies, and e-commerce brands. It connects messaging channels (Instagram, WhatsApp, Telegram) with an intelligent, no-code workflow canvas, CRM contacts engine, and an admin-only influencer referral system.

---

## 🏛 Project Structure

```
Automation_Ai/
├── docker-compose.yml              # Local PostgreSQL 16 (pgvector), Redis 7, MinIO S3
├── .env.example                    # Unified environment configuration template
├── .gitignore                      # Monorepo git exclusion rules
├── scripts/                        # Cross-platform environment automation
│   ├── dev-env.ps1                 # Windows PowerShell automation
│   └── dev-env.sh                  # macOS/Linux/CI automation
├── autoflow-backend/               # Spring Boot 3.3 (Java 21) Modular Monolith
│   ├── build.gradle
│   ├── src/main/java/com/autoflow/
│   └── src/main/resources/
├── autoflow-app/                   # Cross-Platform Flutter Client (Android/iOS/Web)
│   ├── pubspec.yaml
│   ├── lib/
│   ├── android/
│   ├── ios/
│   └── web/
├── autoflow-infra/                 # Infrastructure configurations
│   └── docker/
│       ├── postgres/init-db.sql    # Database extension initialization
│       └── redis/redis.conf        # Redis persistence and memory configuration
└── autoflow-docs/                  # Architecture Decision Records & OpenAPI specs
```

---

## 🚀 Quick Start (Development)

### 1. Verify Prerequisites
- **Java**: 21 LTS (`java -version`)
- **Flutter**: 3.38+ (`flutter --version`)
- **Docker**: Docker Desktop with Compose support
- **Git**: 2.40+

### 2. Initialize Environment
Copy `.env.example` to `.env`:
```powershell
.\scripts\dev-env.ps1 init
```

### 3. Start Local Infrastructure
Launch PostgreSQL 16 (pgvector), Redis 7, and MinIO:
```powershell
.\scripts\dev-env.ps1 up
```
- **PostgreSQL**: `localhost:5432` (`autoflow_db` / `autoflow_user`)
- **Redis**: `localhost:6379` (Auth protected)
- **MinIO S3 API**: `http://localhost:9000`
- **MinIO Console**: `http://localhost:9001` (User: `autoflow_minio_admin`)

---

## 🛡 Security Principles
1. **Zero Secret Leakage**: No API keys, passwords, or signing keystores are committed to Git.
2. **Admin-Only Promo Mutation**: Influencer promo codes and commission rates can only be created or modified by `ADMIN` / `SUPER_ADMIN` roles.
3. **Double-Entry Ledgers**: Financial data is computed with `BigDecimal` and stored in immutable append-only ledgers.
