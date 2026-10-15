#!/usr/bin/env bash
#
# 记账系统 —— 云服务器一次性初始化
# 目标环境：Ubuntu 22.04 / 24.04 LTS，2 核 2G，纯 IP 访问（无域名 / 无 HTTPS）
#
# 形态：PostgreSQL + Redis 跑 Docker，后端 systemd 直跑 jar，Nginx 做入口。
#
# 这个脚本是【幂等】的，可以反复执行：
#   - 已装的软件包会跳过
#   - 已存在的 .env 不会被覆盖（不会把你改过的密码冲掉）
#   - 容器 / 服务用 up -d 与 restart，不做破坏性重建
#
# 用法（在服务器上，root）：
#   bash /opt/jizhang/provision.sh
#
# 前置：先用 deploy/prod/deploy.ps1 把 jizhang-api.jar 与 admin-web 产物传到 /opt/jizhang

set -euo pipefail

APP_DIR=/opt/jizhang
SERVICE_USER=jizhang
LOG_DIR=/var/log/jizhang
NGINX_SITE=/etc/nginx/sites-available/jizhang.conf
UNIT_FILE=/etc/systemd/system/jizhang-api.service

log()  { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\n\033[1;33m[!] %s\033[0m\n' "$*"; }
die()  { printf '\n\033[1;31m[x] %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 执行： sudo bash $0"

export DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------------------
# 0. swap —— 2G 内存跑 PG + JVM，没有 swap 会被 OOM Killer 随机打死进程
# ---------------------------------------------------------------------------
if swapon --show 2>/dev/null | grep -q .; then
  log "已存在 swap，跳过"
else
  log "创建 2G swap"
  if ! fallocate -l 2G /swapfile 2>/dev/null; then
    dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  fi
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  # 尽量少用 swap（只在内存真吃紧时兜底），否则 JVM 会被换出去导致卡顿
  sysctl -w vm.swappiness=10 >/dev/null
  grep -q '^vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf
fi

# ---------------------------------------------------------------------------
# 1. 基础软件包
# ---------------------------------------------------------------------------
log "更新 apt 并安装基础依赖"
apt-get update -qq
apt-get install -y -qq curl ca-certificates gnupg openssl nginx

# ---------------------------------------------------------------------------
# 2. JDK 21
# ---------------------------------------------------------------------------
install_jdk() {
  # Ubuntu 22.04 (jammy-updates) / 24.04 都有 openjdk-21，优先走官方源
  if apt-get install -y -qq openjdk-21-jre-headless 2>/dev/null \
     && java -version 2>&1 | grep -q '"21'; then
    return 0
  fi

  warn "apt 源里没有 openjdk-21，改装 Adoptium Temurin 21"
  mkdir -p /etc/apt/keyrings
  curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
  # shellcheck disable=SC1091
  . /etc/os-release
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb ${VERSION_CODENAME} main" \
    > /etc/apt/sources.list.d/adoptium.list
  apt-get update -qq
  apt-get install -y -qq temurin-21-jre
}

if java -version 2>&1 | grep -q '"21'; then
  log "JDK 21 已安装，跳过"
else
  log "安装 JDK 21"
  install_jdk
fi
# 用 sed 而不是 head：head -n1 会提前关管道，java 写第 2 行时收到 SIGPIPE，
# 在 set -o pipefail 下会让整个脚本直接退出
java -version 2>&1 | sed -n '1p'

# ---------------------------------------------------------------------------
# 3. Docker
# ---------------------------------------------------------------------------
if command -v docker >/dev/null 2>&1; then
  log "Docker 已安装，跳过"
else
  log "安装 Docker（官方 get.docker.com 脚本）"
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker >/dev/null 2>&1
docker --version

# ---------------------------------------------------------------------------
# 4. 运行用户与目录
# ---------------------------------------------------------------------------
log "创建运行用户与目录"
id -u "$SERVICE_USER" >/dev/null 2>&1 \
  || useradd --system --create-home --shell /usr/sbin/nologin "$SERVICE_USER"

mkdir -p "$APP_DIR/bin" "$APP_DIR/admin-web" "$LOG_DIR"

# Nginx 以 www-data 身份读静态资源，给到 o+rX 即可
chmod 755 "$APP_DIR"
chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR/bin" "$LOG_DIR"
chown -R root:root "$APP_DIR/admin-web"
chmod -R a+rX "$APP_DIR/admin-web"

# ---------------------------------------------------------------------------
# 5. 生产环境变量（随机强密钥，只生成一次）
# ---------------------------------------------------------------------------
if [ -f "$APP_DIR/.env" ]; then
  log "$APP_DIR/.env 已存在，保留现有密钥"
else
  log "生成 $APP_DIR/.env（随机密码与 JWT 密钥）"
  PG_PASS="$(openssl rand -hex 16)"
  JWT_SECRET="$(openssl rand -hex 32)"
  cat > "$APP_DIR/.env" <<EOF
# 由 provision.sh 自动生成于 $(date -u '+%Y-%m-%dT%H:%M:%SZ')
# 同时被 systemd（EnvironmentFile）与 docker compose（同目录 .env）读取。
# 格式必须是纯净的 KEY=value：不能写 export，值里不要出现 # 。

POSTGRES_DB=jizhang
POSTGRES_USER=jizhang
POSTGRES_PASSWORD=${PG_PASS}

DB_HOST=127.0.0.1
DB_PORT=5432
DB_NAME=jizhang
DB_USER=jizhang
DB_PASSWORD=${PG_PASS}

REDIS_HOST=127.0.0.1
REDIS_PORT=6379

JIZHANG_JWT_SECRET=${JWT_SECRET}
EOF
  chown root:root "$APP_DIR/.env"
  chmod 600 "$APP_DIR/.env"
fi

for f in docker-compose.prod.yml nginx-jizhang.conf jizhang-api.service wait-for-db.sh provision.sh; do
  [ -f "$APP_DIR/$f" ] || die "$APP_DIR/$f 不存在，请先运行 deploy/prod/deploy.ps1 上传文件"
done
[ -f "$APP_DIR/bin/jizhang-api.jar" ] \
  || die "$APP_DIR/bin/jizhang-api.jar 不存在，请先运行 deploy/prod/deploy.ps1 上传产物"

chmod +x "$APP_DIR/wait-for-db.sh" "$APP_DIR/provision.sh"

# ---------------------------------------------------------------------------
# 6. 起 PostgreSQL / Redis
# ---------------------------------------------------------------------------
log "启动 PostgreSQL 与 Redis 容器"
cd "$APP_DIR"
docker compose -f docker-compose.prod.yml up -d

log "等待 PostgreSQL 就绪"
DB_READY=0
for _ in $(seq 1 60); do
  if docker exec jizhang-postgres pg_isready -U "$(grep -E '^DB_USER=' "$APP_DIR/.env" | cut -d= -f2)" \
       -d "$(grep -E '^DB_NAME=' "$APP_DIR/.env" | cut -d= -f2)" >/dev/null 2>&1; then
    DB_READY=1
    break
  fi
  sleep 2
done
[ "$DB_READY" -eq 1 ] || die "PostgreSQL 在 120 秒内没有就绪，看日志： docker logs jizhang-postgres"
echo "PostgreSQL OK"

# ---------------------------------------------------------------------------
# 7. Nginx
# ---------------------------------------------------------------------------
log "安装 Nginx 站点配置"
cp "$APP_DIR/nginx-jizhang.conf" "$NGINX_SITE"
ln -sf "$NGINX_SITE" /etc/nginx/sites-enabled/jizhang.conf
# 默认站点同样监听 80 default_server，会和我们的配置冲突，必须移除
rm -f /etc/nginx/sites-enabled/default

nginx -t
systemctl enable --now nginx >/dev/null 2>&1
systemctl reload nginx
echo "Nginx OK"

# ---------------------------------------------------------------------------
# 8. 后端 systemd 服务
# ---------------------------------------------------------------------------
log "安装并启动 jizhang-api 服务"
cp "$APP_DIR/jizhang-api.service" "$UNIT_FILE"
systemctl daemon-reload
systemctl enable jizhang-api >/dev/null 2>&1
systemctl restart jizhang-api

# ---------------------------------------------------------------------------
# 9. 健康检查
# ---------------------------------------------------------------------------
log "等待后端就绪（首次启动要建表，可能 30 秒左右）"
OK=0
for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    OK=1
    break
  fi
  sleep 2
done

if [ "$OK" -eq 1 ]; then
  PUB_IP="$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || echo '<公网IP>')"
  cat <<EOF

$(printf '\033[1;32m')================ 部署完成 ================$(printf '\033[0m')

  管理后台    http://${PUB_IP}/
  （80 被运营商拦时，需先在安全组放行 8081）http://${PUB_IP}:8081/
  接口健康    http://${PUB_IP}/health
  Android 的 apiBaseUrl 填： http://${PUB_IP}/api/v1/

  默认管理员  admin / admin123456   ← 登录后立刻改密码

  ── 常用命令 ──
  查看后端日志   journalctl -u jizhang-api -f
  查看容器       cd /opt/jizhang && docker compose -f docker-compose.prod.yml ps
  数据库控制台   docker exec -it jizhang-postgres psql -U jizhang -d jizhang
  重启后端       systemctl restart jizhang-api
  改环境变量     vim /opt/jizhang/.env && systemctl restart jizhang-api

EOF
else
  warn "后端 120 秒内没起来，看日志定位："
  echo "  journalctl -u jizhang-api -n 120 --no-pager"
  echo "  tail -n 120 /var/log/jizhang/jizhang-api.log"
  exit 1
fi
