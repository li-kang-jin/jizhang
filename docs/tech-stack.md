# 记账系统 - 技术选型

> 版本：v1.0
> 更新日期：2026-10-14

## 1. 总体架构

```
Android 原生 App ──┐
                   ├─→ Nginx ─→ jizhang-api (Spring Boot 模块化单体)
管理后台 (Web) ────┘                ├─ PostgreSQL 16
                                    ├─ Redis 7
                                    ├─ RabbitMQ
                                    └─ MinIO / 阿里云 OSS
```

设计原则：

1. **后端先做模块化单体**，不做微服务。模块内高内聚，模块间通过 service 接口通信，后期按压力单独拆出。
2. **接口版本前缀 `/api/v1`**，管理后台与 C 端接口按路径区分（`/api/v1/admin/**`、`/api/v1/app/**`）。
3. **金额一律用 `bigint` 存储「分」**，禁止 float/double。
4. **主键由客户端生成 UUID**，天然支持离线创建与幂等同步。
5. **所有业务表带软删除与审计字段**，后期加功能不改表结构。

## 2. 后端

| 层面 | 选型 | 版本 | 说明 |
| --- | --- | --- | --- |
| 运行时 | OpenJDK (Temurin) | 21 LTS | 已确认本地为 21.0.11 |
| 框架 | Spring Boot | 3.3.x | Web / Validation / Actuator |
| 构建 | Maven | 3.9.x | 多模块 |
| 安全 | Spring Security + JWT | - | Access Token + Refresh Token |
| 持久化 | MyBatis-Plus | 3.5.x | 复杂报表 SQL 可控 |
| 数据库 | PostgreSQL | 16 | 金额用 bigint；JSONB 存扩展字段 |
| 缓存 | Redis | 7 | Token 黑名单、验证码、热点统计 |
| 消息队列 | RabbitMQ | 3.13 | 报表生成、账单提醒异步化 |
| 定时任务 | XXL-Job | 2.4.x | 周期性账单、到期提醒 |
| 对象存储 | MinIO（开发）/ OSS（生产） | - | 票据截图、导出文件 |
| 接口文档 | SpringDoc OpenAPI (Swagger UI) | 2.6.0 | 前后端契约，访问 /swagger-ui.html |
| 参数校验 | Jakarta Validation | - | 注解式校验 |
| 日志 | Logback + MDC traceId | - | 链路追踪 |
| 部署 | Docker + Docker Compose | - | 后期平滑迁 K8s |

## 3. 管理后台（Web）

| 层面 | 选型 |
| --- | --- |
| 框架 | Vue 3 + Vite |
| 语言 | TypeScript |
| UI 组件 | Element Plus |
| 状态管理 | Pinia |
| 路由 | Vue Router（动态路由 + 权限） |
| 请求 | Axios（统一鉴权、错误码、Loading） |
| 图表 | ECharts |
| 代码规范 | ESLint + Prettier |

## 4. Android 端（C 端）

| 层面 | 选型 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 架构 | MVVM + Clean Architecture + Repository |
| 异步 | Coroutines + Flow |
| 依赖注入 | Hilt |
| 网络 | Retrofit + OkHttp + Kotlinx Serialization |
| 本地存储 | Room（离线记账核心）+ DataStore |
| 后台任务 | WorkManager（后台同步、本地提醒） |
| 导航 | Navigation Compose |
| 图片 | Coil |
| 最低版本 | Android 8.0 / API 26 |

## 5. 本地环境要求

| 工具 | 版本 | 状态 |
| --- | --- | --- |
| JDK | 21 LTS | 已安装 21.0.11 |
| Maven | 3.9.9 | 已安装至 `D:\code2\tools\apache-maven-3.9.9` |
| Node.js | 20+ | 已安装 v22.22.2 |
| Git | 2.x | 已安装 2.55.0 |
| Docker | 28.x | 已安装，用于本地起 PostgreSQL / Redis |
| PostgreSQL | 16 | 已通过 `deploy/docker-compose.yml` 运行 |
| Redis | 7 | 编排已定义，暂未启用（令牌黑名单当前走内存实现） |
| Android Studio | 最新稳定版 | 待安装 |

## 6. 目录结构

```
jizhang/
├── docs/                # 设计文档（数据模型、接口契约）
├── server/              # Spring Boot 多模块后端
│   ├── jizhang-common/      # 通用工具、统一响应、异常、常量
│   ├── jizhang-dao/         # Entity、Mapper、数据库访问
│   ├── jizhang-service/     # 业务逻辑
│   └── jizhang-api/         # Controller、启动类、配置
├── admin-web/           # Vue3 + TS + Element Plus 管理后台
├── android/             # Kotlin + Compose 原生 App（离线记账）
└── deploy/              # docker-compose、Nginx、CI 脚本
```
