#!/usr/bin/env bash
#
# 等 PostgreSQL 端口可用再启动后端。
#
# 为什么单独放一个脚本，而不是写在 systemd 的 ExecStartPre 里：
#   systemd 会对 Exec* 行做变量展开（$FOO / ${FOO}），
#   `$(seq 1 60)` 这种命令替换会被它误判，必须写成 $$ 转义，极易踩坑。
#   放进独立脚本后 unit 里只有一行 ExecStartPre=/opt/jizhang/wait-for-db.sh，干净可靠。
#
# 由 deploy/prod/provision.sh 自动 chmod +x。

set -u

PORT=5432
MAX_WAIT_SECONDS=120
INTERVAL=2
attempts=$((MAX_WAIT_SECONDS / INTERVAL))

for _ in $(seq 1 "$attempts"); do
    if (echo > "/dev/tcp/127.0.0.1/${PORT}") >/dev/null 2>&1; then
        exit 0
    fi
    sleep "$INTERVAL"
done

echo "PostgreSQL 127.0.0.1:${PORT} 在 ${MAX_WAIT_SECONDS} 秒内未就绪" >&2
exit 1
