# 部署手册：前端管理后台 + 后端代码怎么更新到服务器

> 读者：第一次接触这台服务器的人。
> 读完应该能做到三件事：看懂一键脚本做了什么、手动完成一次发布、发布出问题时回滚。
>
> 关联文件：`deploy/prod/deploy.ps1`（本机一键）、`deploy/prod/provision.sh`（服务器初始化）、
> `deploy/prod/jizhang-api.service`、`deploy/prod/nginx-jizhang.conf`、`deploy/prod/docker-compose.prod.yml`。

## 1. 先看全景：一次发布到底动了什么

服务器上跑着 **四个**东西，它们互相独立，这一点决定了「只改前端」和「改了后端」的发布方式完全不同：

```mermaid
flowchart LR
    U[浏览器 / Android] --> N[Nginx :80 / :8081]
    N -- /api/ 反代 --> J[Spring Boot jar<br/>127.0.0.1:8080<br/>systemd: jizhang-api]
    N -- / 静态文件 --> S[/opt/jizhang/admin-web<br/>Vue 构建产物]
    J --> P[(PostgreSQL<br/>容器 :5432)]
    J --> R[(Redis<br/>容器 :6379)]
```

| 组件 | 跑在哪 | 代码变更后需要做什么 |
| --- | --- | --- |
| 管理后台（Vue） | Nginx 静态目录 | 换掉 `admin-web/` 里的文件（**不用重启任何服务**） |
| 后端（Spring Boot） | systemd 直跑 jar | 换掉 jar + `systemctl restart jizhang-api` |
| Nginx 配置 | `/etc/nginx/sites-available/jizhang.conf` | `nginx -t` + `systemctl reload nginx` |
| 数据库表结构 | `schema.sql` 随 jar 打包 | **不用管**：后端启动时自动执行（幂等） |

## 2. 服务器目录布局

```
/opt/jizhang/
├── bin/jizhang-api.jar        # 后端产物，属主 jizhang:jizhang（服务以 jizhang 身份运行）
├── admin-web/                 # 前端产物，属主 root:root、权限 a+rX（Nginx 以 www-data 读）
│   ├── index.html             # 不缓存
│   └── assets/*.js|*.css      # 带 hash，长缓存 30 天
├── .env                       # DB 密码 / JWT 密钥，root:root 600，只生成一次，之后永不覆盖
├── docker-compose.prod.yml    # PG + Redis
├── nginx-jizhang.conf         # 会被复制到 /etc/nginx/sites-available/
├── jizhang-api.service        # 会被复制到 /etc/systemd/system/
├── wait-for-db.sh             # 后端启动前等 PG 就绪
└── provision.sh               # 幂等的服务器初始化脚本

/etc/systemd/system/jizhang-api.service      # systemd 单元
/etc/nginx/sites-available/jizhang.conf      # Nginx 站点（sites-enabled 里是软链）
/var/log/jizhang/                            # 后端日志（journal 之外的另一份）
```

**为什么 jar 和前端目录属主不一样**：jar 由 systemd 以 `jizhang` 用户执行，所以 jar 必须能被它读；
前端由 Nginx 以 `www-data` 读，只需要 `a+rX`，不需要写权限。手动上传时如果属主搞错，
现象是「服务起不来（Permission denied）」或「页面 403」。

## 3. 一键脚本做了什么（读懂它就懂了手动流程）

`deploy/prod/deploy.ps1` 在**本机**执行，分 5 个阶段：

### 阶段 0：连通性与占用检查

```powershell
ssh -o BatchMode=yes -o ConnectTimeout=10 jz-prod 'echo OK'
```

用的是 `~/.ssh/config` 里的别名 `jz-prod`，密钥登录、免密码。**没有配这个别名，脚本第一行就会失败。**

然后检查 `server/jizhang-api/target/jizhang-api.jar` 是否被本机的 java 进程占用——
本机常年跑着一个 dev 后端，它会锁住这个 jar，导致 `mvn clean` 直接失败
（Windows 上被打开的文件不能删除）。脚本遇到这种情况会明确报「请先停掉本地后端再部署」。

### 阶段 1：构建

```powershell
mvn -s deploy\maven-settings.xml -f server\pom.xml clean package -DskipTests
npm run build    # 在 admin-web 下，即 vue-tsc -b && vite build
```

产物只有两个：`server/jizhang-api/target/jizhang-api.jar`、`admin-web/dist/`。

### 阶段 2：组装上传包

把「jar + 前端产物 + 4 个配置/脚本」放进 `.staging/`，再用 `tar -czf` 压成一个文件。
**为什么打成一个包**：几十个小文件逐个 scp，每个都要一次握手，慢且容易中断；
一个 tgz 传一次，还能保证「服务器上要么全换、要么全不换」。

### 阶段 3：上传

```powershell
scp payload.tgz jz-prod:/tmp/jizhang-payload.tgz
```

传到 `/tmp` 而不是直接传到 `/opt/jizhang`：普通 ssh 用户对 `/opt/jizhang` 没有写权限，
需要 `sudo` 才能放进去，而 `scp` 不能 sudo。

### 阶段 4：服务器上解包 + 执行 provision.sh

```bash
sudo -n tar -xzf /tmp/jizhang-payload.tgz -C /opt/jizhang
sudo -n bash -n /opt/jizhang/provision.sh && sudo -n bash /opt/jizhang/provision.sh
```

`provision.sh` 是幂等的，每次发布都会完整跑一遍，实际做的事：

1. 有 swap 就跳过（2G 内存跑 PG + JVM 必须有 swap）；
2. 缺的软件包才装（JDK 21、Docker、Nginx）；
3. **`chown` 修正权限**：`bin/` 给 `jizhang`，`admin-web/` 给 `root:root` + `a+rX`；
4. `.env` 已存在就保留（**不会覆盖你改过的密码**）；
5. `docker compose up -d` 起 PG / Redis，并等 `pg_isready`；
6. 复制 Nginx 配置 → `nginx -t` → `reload`；
7. 复制 systemd 单元 → `daemon-reload` → `systemctl restart jizhang-api`；
8. 轮询 `http://127.0.0.1:8080/actuator/health`，最多等 120 秒，超时就打印排查命令并退出 1。

### 阶段 5：清理本机临时文件

删掉 `.staging/` 和 `payload.tgz`。

## 4. 手动上传：三套方案

> 下面命令里的 `jz-prod` 换成你的 ssh 别名或 `root@1.2.3.4`。
> 如果 ssh 登录用户就是 root，`sudo -n` 可以去掉；否则保留。

### 方案 A：只换后端 jar（最常见）

改动范围：Java 代码、`schema.sql`、`application-prod.yml`。

**本机：**

```powershell
cd d:\code2\jizhang

# 1. 构建（-DskipTests 跳过测试，赶时间时用；正式发布建议也跑一遍测试）
& 'D:\code2\tools\apache-maven-3.9.9\bin\mvn.cmd' `
    -s deploy\maven-settings.xml -f server\pom.xml clean package -DskipTests

# 2. 检查产物时间戳，确认是新打的
Get-Item server\jizhang-api\target\jizhang-api.jar | Select-Object Name,Length,LastWriteTime

# 3. 传到服务器 /tmp（普通用户可写）
scp server\jizhang-api\target\jizhang-api.jar jz-prod:/tmp/jizhang-api.jar
```

**服务器（ssh 上去或一条 ssh 执行）：**

```bash
# 1. 先备份旧 jar，出问题好回滚
sudo cp -a /opt/jizhang/bin/jizhang-api.jar /opt/jizhang/bin/jizhang-api.jar.bak.$(date +%Y%m%d%H%M)

# 2. 停服务 —— 必须先停
sudo systemctl stop jizhang-api

# 3. 用 install 而不是 cp：它能一次性把权限和属主设对
sudo install -m 640 -o jizhang -g jizhang /tmp/jizhang-api.jar /opt/jizhang/bin/jizhang-api.jar

# 4. 启动
sudo systemctl start jizhang-api

# 5. 看日志，确认起来了
journalctl -u jizhang-api -n 60 --no-pager
curl -s http://127.0.0.1:8080/actuator/health; echo
```

**为什么要先 `stop`**：运行中的 JVM 已经把 jar 打开着，`cp` 是原地截断同一个文件，
JVM 后续再去读类就可能读到半个文件（表现为莫名其妙的 `ClassNotFoundException` / 校验错误）。
停掉服务再换文件是最省心的做法。

### 方案 B：只换前端（Vue 管理后台）

改动范围：`admin-web/src/**`、`vite.config.ts`、样式。**不需要重启后端。**

**本机：**

```powershell
cd d:\code2\jizhang\admin-web
npm run build
# 产物：d:\code2\jizhang\admin-web\dist

# 建一个临时目录并上传（先传临时目录，避免替换到一半断了）
ssh jz-prod 'mkdir -p /tmp/admin-web-new && rm -rf /tmp/admin-web-new/*'
scp -r dist\* jz-prod:/tmp/admin-web-new/
```

**服务器：**

```bash
# 1. 备份当前版本（回滚用）
sudo cp -a /opt/jizhang/admin-web /opt/jizhang/admin-web.bak.$(date +%Y%m%d%H%M)

# 2. 替换（先清空再拷贝，避免旧 hash 文件残留）
sudo rm -rf /opt/jizhang/admin-web/*
sudo cp -r /tmp/admin-web-new/* /opt/jizhang/admin-web/

# 3. 修权限：Nginx 以 www-data 读，必须 a+rX
sudo chown -R root:root /opt/jizhang/admin-web
sudo chmod -R a+rX /opt/jizhang/admin-web

# 4. 确认 index.html 是新的
ls -l /opt/jizhang/admin-web/index.html
```

静态文件换完即刻生效，**不用 reload Nginx**（只有改 `nginx-jizhang.conf` 才需要 `nginx -t && systemctl reload nginx`）。

浏览器如果还是旧页面，先强制刷新（Ctrl+F5）——`index.html` 配的是 `no-cache`，
但浏览器缓存策略不总是守规矩。

### 方案 C：完整发布（等价于一键脚本，手打）

适合「改了后端 + 前端 + 配置」或想搞清楚脚本细节的时候。

**本机（PowerShell）：**

```powershell
cd d:\code2\jizhang

# 构建
& 'D:\code2\tools\apache-maven-3.9.9\bin\mvn.cmd' `
    -s deploy\maven-settings.xml -f server\pom.xml clean package -DskipTests
Push-Location admin-web; npm run build; Pop-Location

# 组装
$s = 'deploy\prod\.staging'
Remove-Item $s -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item deploy\prod\payload.tgz -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$s\bin", "$s\admin-web" -Force | Out-Null
Copy-Item server\jizhang-api\target\jizhang-api.jar "$s\bin\"
Copy-Item admin-web\dist\* "$s\admin-web" -Recurse
Copy-Item deploy\prod\docker-compose.prod.yml, deploy\prod\nginx-jizhang.conf, `
          deploy\prod\jizhang-api.service, deploy\prod\provision.sh, `
          deploy\prod\wait-for-db.sh $s

# 打包（-C 保证包内是相对路径）
tar -czf deploy\prod\payload.tgz -C $s .
(Get-Item deploy\prod\payload.tgz).Length / 1MB

# 上传
scp deploy\prod\payload.tgz jz-prod:/tmp/jizhang-payload.tgz
```

**服务器：**

```bash
# 1) 解包（先落地文件，后面的脚本才存在）
sudo tar -xzf /tmp/jizhang-payload.tgz -C /opt/jizhang

# 2) 语法检查：脚本本身有问题时立刻停，而不是跑到一半把服务器改坏
sudo bash -n /opt/jizhang/provision.sh
sudo bash -n /opt/jizhang/wait-for-db.sh

# 3) 幂等初始化：装依赖、起容器、装配置、重启后端、等健康检查
sudo bash /opt/jizhang/provision.sh

# 4) 清理
rm -f /tmp/jizhang-payload.tgz
```

如果**只想换文件、不想跑 provision**（例如配置一个都没改）：

```bash
sudo tar -xzf /tmp/jizhang-payload.tgz -C /opt/jizhang
sudo chown -R jizhang:jizhang /opt/jizhang/bin
sudo chown -R root:root /opt/jizhang/admin-web && sudo chmod -R a+rX /opt/jizhang/admin-web
sudo systemctl restart jizhang-api
```

### 方案 D：图形工具（WinSCP / FileZilla）

不想记命令时可以用，但有两个坑必看：

1. **传输模式选二进制（Binary）**，不要自动/文本，否则 jar 会被改行尾、直接起不来；
2. **目标目录权限**：WinSCP 用 root 登录时可以直接传到 `/opt/jizhang/bin/`、`/opt/jizhang/admin-web/`；
   用普通用户登录时没有写权限，先传到 `/tmp`，再在 SSH 终端里 `sudo cp` / `install`。

典型操作：

1. 本机打包/构建好 `jizhang-api.jar`、`dist/`；
2. 用 WinSCP 连 `jz-prod`，把 jar 拖到 `/tmp/`；
3. 开 WinSCP 自带的终端（Ctrl+T），执行方案 A 的第 2～5 步；
4. 前端：把 `dist` 里所有文件拖到 `/tmp/admin-web-new/`，再按方案 B 第 2～3 步替换。

**永远不要在图形工具里直接覆盖 `.env`**：里面是数据库密码和 JWT 密钥，覆盖后旧数据连不上、
所有已登录用户的 token 全部失效。

## 5. 回滚

发布前备份、出问题三分钟撤回，比「现场排查」快得多。

```bash
# 后端
sudo systemctl stop jizhang-api
sudo cp /opt/jizhang/bin/jizhang-api.jar.bak.202610191730 /opt/jizhang/bin/jizhang-api.jar
sudo systemctl start jizhang-api

# 前端
sudo rm -rf /opt/jizhang/admin-web/*
sudo cp -a /opt/jizhang/admin-web.bak.202610191730/. /opt/jizhang/admin-web/
```

**数据库不能靠回滚文件**：`schema.sql` 里的变更都是幂等的 `ADD COLUMN IF NOT EXISTS` 这一类，
只增不减；如果某次改动真的删了列，要单独准备 SQL 回滚脚本。这也是为什么表结构变更要
「先加列、再改代码、最后（若干版本后）再删旧列」。

## 6. 发布后必须做的验证

```bash
# 1) 服务是否活着
systemctl is-active jizhang-api            # 期望 active
curl -s http://127.0.0.1:8080/actuator/health; echo

# 2) 从公网入口再验一次（走 Nginx）
curl -s http://<公网IP>/health; echo

# 3) 最近日志有没有异常
journalctl -u jizhang-api -n 80 --no-pager

# 4) 管理后台页面（拿到 200 即静态资源 OK）
curl -s -o /dev/null -w '%{http_code}\n' http://<公网IP>/

# 5) 接口鉴权是否正常（期望 401，不是 404/403/500）
curl -s -o /dev/null -w '%{http_code}\n' http://<公网IP>/api/v1/auth/me
```

`401` 是**好**结果：说明路由存在且被安全层保护。`404` 说明接口没部署上去，
`500` 说明后端内部报错（多半是数据库列/表没跟上，看日志第一条 `Caused by`）。

## 7. 新手最常踩的 8 个坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `mvn clean` 失败，提示文件被占用 | 本机 dev 后端锁住了 target 下的 jar | 先停本地后端（`deploy/start-all.ps1` 启的进程） |
| ssh 执行复杂命令报 `unexpected EOF` | Windows 的 `ssh.exe` 会按 CRT 规则重解析引号，把 `"` 吃掉 | 命令尽量简单；或 `@'脚本'@ \| ssh host bash -s` 从标准输入喂脚本 |
| 服务起来后立刻退出 | 属主/权限不对（jar 不归 `jizhang` 可读），或 `.env` 缺失 | `journalctl -u jizhang-api -n 60` 看首条错误 |
| 改了 unit 文件但没生效 | 没执行 `systemctl daemon-reload` | 改 `/etc/systemd/system/*.service` 后必须 reload |
| 页面还是旧的 | 浏览器缓存，或替换时用了 `cp -r dist/* /opt/.../` 而没先清空 | Ctrl+F5；替换前 `rm -rf` 目标目录内容 |
| 80 打不开但服务器本地正常 | 云厂商/运营商拦了 80 | 用 `http://IP:8081/`（安全组需放行 8081） |
| Nginx 起不来报 `EADDRINUSE` | 兜底端口不能写 8080，那是后端自己监听的 | 保持 8081，别改 |
| 部署后所有用户被登出 | `.env` 被覆盖，JWT 密钥变了 | 从备份恢复 `.env` 再 `systemctl restart jizhang-api` |

## 8. 场景速查

| 你改了什么 | 需要构建 | 需要上传 | 需要重启 | 需要 reload nginx |
| --- | --- | --- | --- | --- |
| Java 代码 / `schema.sql` / `application-*.yml` | Maven | jar | `systemctl restart jizhang-api` | 否 |
| Vue 管理后台 | `npm run build` | `dist/*` | 否 | 否 |
| `nginx-jizhang.conf` | 否 | 该文件 | 否 | `nginx -t && systemctl reload nginx` |
| `jizhang-api.service` | 否 | 该文件 | `daemon-reload` + restart | 否 |
| `docker-compose.prod.yml` | 否 | 该文件 | `docker compose up -d` | 否 |
| Android App | Gradle | APK 装机 | — | — |
| 什么都没改，只想重启后端 | 否 | 否 | `systemctl restart jizhang-api` | 否 |

## 9. 完整发布检查清单

发布前：

- [ ] 本地构建通过（`mvn package` / `npm run build` 都 exit 0）
- [ ] `git status` 确认没把调试代码、临时文件带上去
- [ ] 涉及表结构变更：只用 `IF NOT EXISTS` 这类幂等语句
- [ ] 服务器磁盘足够（`df -h /`，jar 约几十 MB，旧备份别攒太多）

发布中：

- [ ] 备份旧 jar / 旧 `admin-web`
- [ ] 先传 `/tmp`，再 `install`/`cp` 到目标目录
- [ ] 修正属主与权限（jar → `jizhang:jizhang`，前端 → `root:root` + `a+rX`）
- [ ] `systemctl restart` 后确认 `active`

发布后：

- [ ] `/health` 返回 `UP`
- [ ] `/api/v1/auth/me` 返回 401（路由 + 鉴权都在）
- [ ] 管理后台能打开、能登录
- [ ] 日志无 `ERROR` / `Caused by`
- [ ] 关键业务路径点一遍（记账、同步、后台列表）

## 10. 命令速查

```bash
# —— 服务 ——
systemctl status jizhang-api --no-pager
sudo systemctl restart jizhang-api
journalctl -u jizhang-api -f
tail -f /var/log/jizhang/jizhang-api.log

# —— Nginx ——
sudo nginx -t
sudo systemctl reload nginx
tail -f /var/log/nginx/jizhang.error.log

# —— 数据库 ——
cd /opt/jizhang && sudo docker compose -f docker-compose.prod.yml ps
sudo docker exec -it jizhang-postgres psql -U jizhang -d jizhang
sudo docker logs --tail 100 jizhang-postgres

# —— 文件与权限 ——
ls -l /opt/jizhang/bin /opt/jizhang/admin-web | head
sudo chown jizhang:jizhang /opt/jizhang/bin/*
sudo chown -R root:root /opt/jizhang/admin-web && sudo chmod -R a+rX /opt/jizhang/admin-web

# —— 环境变量（改完必须重启后端；PG 密码改了还要 up -d 重建容器）——
sudo vim /opt/jizhang/.env
sudo systemctl restart jizhang-api
```

## 11. 本机开发环境（别和线上搞混）

`deploy/start-all.ps1` 是**在你自己电脑上**跑一套开发环境：本地 PG/Redis（`deploy/docker-compose.yml`）、
本地后端 jar、Vite 开发服务器。它和线上部署没有任何关系，只是恰好也用 `.env` 与 compose。

发布时唯一需要注意的关系是：**本地后端在跑会锁住 jar**，先停掉再构建。
