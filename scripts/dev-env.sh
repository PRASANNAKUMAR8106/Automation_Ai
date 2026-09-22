#!/usr/bin/env bash
# ==============================================================================
# AutoFlow AI - Unix/macOS Local Environment Automation Script
# Usage:
#   ./scripts/dev-env.sh check      - Verify installed developer toolchain
#   ./scripts/dev-env.sh init       - Initialize .env configuration file
#   ./scripts/dev-env.sh up         - Spin up PostgreSQL, Redis, and MinIO in Docker
#   ./scripts/dev-env.sh down       - Tear down Docker services
#   ./scripts/dev-env.sh status     - Check status of Docker services and ports
# ==============================================================================

set -eo pipefail

ACTION="${1:-status}"

check_tools() {
    echo -e "\033[0;36m==> Checking AutoFlow AI Local Development Toolchain...\033[0m"
    for tool in java flutter dart docker git; do
        if command -v "$tool" >/dev/null 2>&1; then
            echo -e "  \033[0;32m[OK]\033[0m $tool found at $(command -v "$tool")"
        else
            echo -e "  \033[0;31m[MISSING]\033[0m $tool is not in system PATH!"
        fi
    done
}

init_env() {
    echo -e "\033[0;36m==> Initializing environment configuration...\033[0m"
    if [ -f ".env" ]; then
        echo -e "  \033[0;33m[SKIP]\033[0m .env already exists."
    else
        cp .env.example .env
        echo -e "  \033[0;32m[CREATED]\033[0m Copied .env.example to .env."
    fi
}

start_services() {
    init_env
    echo -e "\033[0;36m==> Starting AutoFlow infrastructure (PostgreSQL 16, Redis 7, MinIO S3)...\033[0m"
    docker compose up -d
    docker compose ps
}

stop_services() {
    echo -e "\033[0;36m==> Stopping AutoFlow infrastructure...\033[0m"
    docker compose down
}

show_status() {
    echo -e "\033[0;36m==> Current AutoFlow Infrastructure Status:\033[0m"
    docker compose ps
}

case "$ACTION" in
    check)  check_tools ;;
    init)   init_env ;;
    up)     start_services ;;
    down)   stop_services ;;
    status) show_status ;;
    *)      echo "Usage: $0 {check|init|up|down|status}" ; exit 1 ;;
esac
