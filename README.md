# 记账系统

一套完整的记账应用：**Android 原生客户端 + 管理后台 + 后端服务**。

支持**离线记账**与**多端同步** —— 断网也能记，联网后自动补传。

## 组成

| 模块 | 技术栈 | 目录 |
| --- | --- | --- |
| 后端服务 | Spring Boot 3.3 · PostgreSQL 16 · MyBatis-Plus · JWT | `server/` |
| 管理后台 | Vue 3 · TypeScript · Element Plus · Pinia · ECharts | `admin-web/` |
| Android 客户端 | Kotlin · Jetpack Compose · Room · Hilt · Retrofit | `android/` |
| 设计文档 | 技术选型 / 数据库设计 / 接口契约 | `docs/` |
| 部署与脚本 | docker-compose · Maven 配置 · 端到端验证脚本 | `deploy/` |

后端接口覆盖认证、账本、账户、分类、交易、报表、预算与固定支出、多端同步、管理后台九个模块。

## 快速开始

### 环境要求

| 工具 | 版本 |
| --- | --- |
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 20+ |
| Docker | 用于启动 PostgreSQL / Redis |
| Android Studio | 最新稳定版（仅运行客户端时需要） |

### 1. 启动依赖服务

```bash
docker compose -f deploy/docker-compose.yml up -d
```

启动 PostgreSQL 16（`localhost:5432`，库 / 用户 / 密码均为 `jizhang`）与 Redis 7。

> 如果数据库卷是全新的，首次连接可能需要手动建库：
> `docker exec jizhang-postgres psql -U jizhang -d postgres -c 'CREATE DATABASE jizhang OWNER jizhang;'`

### 2. 启动后端

```bash
mvn -s deploy/maven-settings.xml -f server/pom.xml clean package -DskipTests
java -jar server/jizhang-api/target/jizhang-api.jar
```

- 服务地址：http://localhost:8080
- 接口文档：http://localhost:8080/swagger-ui.html
- 首次启动会自动建表，并创建默认管理员 `admin / admin123456`

### 3. 启动管理后台

```bash
cd admin-web
npm install
npm run dev
```

访问 http://localhost:5173 （端口被占用时会自动顺延）。

### 4. 运行 Android 客户端

用 Android Studio 打开 `android/` 目录直接运行，或使用命令行：

```bash
cd android
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

客户端默认连接 `http://10.0.2.2:8080`（模拟器访问宿主机的固定地址）。
真机调试需改为电脑的局域网 IP，手机与电脑要在同一 WiFi：

```bash
gradle assembleDebug -PapiBaseUrl=http://192.168.1.5:8080/api/v1/
```

**登录是可选的**：打开 App 即可记账（数据存在本地），需要多端同步时再到「我的」页登录，
本地已有的账单会自动迁移到云端。首次启动会内置 3 个账户与 15 个分类，也可在「账户管理」里自行增删。

App 内置一只叫「小锦锦」的宠物：每记一笔账获得金币，金币累积升级，连续记账累计天数，
本月超支时它会露出担心的表情。可在「个性化」里切换 3 种宠物形象与 4 套主题配色。

## 家庭多人记账

**账本即团队**，不另设「家庭 / 团队」实体：

| 概念 | 说明 |
| --- | --- |
| 个人账本（`type=0`） | 成员只有自己，数据仅自己可见，用于私房钱与私人开销 |
| 家庭账本（`type=1`） | 所有成员都能看到账本内每一笔收支，即「家庭共同财产」 |
| 邀请码 | 所有者在账本里生成 8 位 code，家人在「账本 → 加入家庭账本」输入即加入 |
| 记账人 `createdBy` | 家庭账本的流水显示「爸爸 记的」，统计页按成员汇总「谁花了多少」 |

「个人用」和「多人用」不是两套模式：同一套账本模型，靠账本 `type` 与成员列表区分。
顶部导航栏的**账本切换器**是全 App 的单一数据源，账单 / 统计 / 预算都跟随当前账本。

两个关键约定：

1. **账户不跨账本**。家庭共同财产放家庭账本，私房钱放个人账本，隐私边界天然清晰。
2. **本地离线账单只迁入「我的个人账本」**。登录时若取「账号下第一个账本」，家庭共享后
   很可能正好是家庭账本，会把用户离线记的私人账单直接暴露给全家人。

> V1 刻意不做（见 `docs/api-contract.md` 待实现一节）：待审批加入、家庭实体层（一支成员管多个账本）、
> 共享账本内的私有账户、邀请链接/二维码。

## 端到端验证

`deploy/` 下提供四个脚本，可在后端启动后直接运行：

```bash
# 注册 / 登录 / 账本 / 账户 / 分类 / 交易 / 余额联动 / 登出失效
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/smoke-test.ps1

# 报表（总览、分类占比、趋势、资产）与同步（全量、增量、冲突、删除）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/report-sync-test.ps1

# 预算（总预算 / 分类预算、进度、超支、越权）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/budget-test.ps1

# 固定支出（保存 / 幂等更新 / 参数兜底 / 同步下发 / 删除随同步 / 越权）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/recurring-expense-test.ps1
```

## 核心设计

**金额一律用 `bigint` 存「分」**，不使用浮点类型，避免精度问题。

**主键由客户端生成**（毫秒时间戳左移 20 位 + 20 位随机数），离线创建的交易天然拥有唯一 ID，
服务端按该 ID 做幂等写入，重复推送不会产生脏数据。

**离线优先**：客户端的写操作先落本地 Room 并标记待同步，联网后由同步任务批量推送。
删除同样先在本地打标记再上送，避免断网删除后「删了又回来」。

**同步策略**：

- 增量拉取，服务端返回 `nextCursor`；当交易被条数上限截断时游标停在最后一笔，不会漏数据
- 服务端查询时游标回退 5 秒（重叠窗口），规避「事务提交晚于 `updated_at` 赋值」的可见性问题
- 冲突采用 last-write-wins：服务端版本较新时在 `conflicts` 中返回，客户端以它覆盖本地

## 文档

- [技术选型](docs/tech-stack.md)
- [数据库设计](docs/database-design.md)
- [接口契约](docs/api-contract.md)

## 部署前必读

以下默认值仅供开发使用，生产部署请通过环境变量覆盖：

| 环境变量 | 说明 |
| --- | --- |
| `JIZHANG_JWT_SECRET` | JWT 签名密钥（长度需 ≥ 32 字节） |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | 数据库连接 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接 |

其他已知限制：

- 令牌黑名单当前为**单机内存实现**，多实例部署需替换为 Redis 实现（接口已抽象）
- 客户端 `usesCleartextTraffic` 为开启状态（HTTP 调试用），生产应改用 HTTPS
- 周期账单（到点自动生成账单）、票据上传等功能尚未实现，详见 `docs/api-contract.md` 的「待实现」一节。
  固定支出规则已支持多端同步，但**只做预算内预留、不自动记账**
