# AutoFlow AI — Environment Variables Dictionary

| Variable Name | Required | Default / Example | Purpose |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Yes | `dev` | Active Spring profile (`dev`, `staging`, `prod`) |
| `APP_PORT` | Yes | `8080` | HTTP port for the Spring Boot application |
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | Yes | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `autoflow_db` | Primary database name |
| `DB_USER` | Yes | `autoflow_user` | Database user account |
| `DB_PASSWORD` | Yes | `autoflow_secure_pass_2026` | Database user password |
| `REDIS_HOST` | Yes | `localhost` | Redis server hostname |
| `REDIS_PORT` | Yes | `6379` | Redis server port |
| `REDIS_PASSWORD` | Yes | `autoflow_redis_pass_2026` | Redis authentication password |
| `JWT_SECRET` | Yes | (Base64 512-bit string) | Secret key for signing HS512 / RS256 JWT tokens |
| `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | Yes | `900` | Access token lifespan (15 minutes) |
| `JWT_REFRESH_TOKEN_EXPIRATION_SECONDS` | Yes | `2592000` | Refresh token lifespan (30 days) |
| `S3_ENDPOINT` | Yes | `http://localhost:9000` | S3 API endpoint (MinIO locally or AWS S3) |
| `S3_ACCESS_KEY` | Yes | `autoflow_minio_admin` | S3 access key |
| `S3_SECRET_KEY` | Yes | `autoflow_minio_secret_2026`| S3 secret key |
| `RAZORPAY_KEY_ID` | Yes | `rzp_test_...` | Razorpay API Key ID (Test mode) |
| `RAZORPAY_KEY_SECRET` | Yes | `...` | Razorpay API Secret |
| `RAZORPAY_WEBHOOK_SECRET` | Yes | `...` | HMAC-SHA256 signature verification key for webhooks |
| `META_APP_ID` | Optional in MVP | `...` | Meta Developer App ID |
| `META_APP_SECRET` | Optional in MVP | `...` | Meta Developer App Secret |
| `META_VERIFY_TOKEN` | Optional in MVP | `...` | Meta Webhook verification token |
| `OPENAI_API_KEY` | Optional in MVP | `sk-proj-...` | OpenAI API key for workflow generation and AI nodes |
