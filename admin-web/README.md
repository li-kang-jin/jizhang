# 记账系统 · 管理后台

基于 Vue 3 + TypeScript + Vite + Element Plus 的运营管理后台。

## 开发

```bash
npm install
npm run dev      # http://localhost:5173，/api 自动代理到 localhost:8080
```

## 构建

```bash
npm run build    # 含 vue-tsc 类型检查，产物输出到 dist/
```

## 登录

默认管理员账号：`admin / admin123456`（由后端首次启动时自动创建）。

## 功能

| 页面 | 说明 |
| --- | --- |
| 数据看板 | 平台总览（用户 / 账本 / 交易笔数 / 累计金额）+ 增长趋势折线图 |
| 用户管理 | 列表查询、按状态与角色筛选、启用 / 禁用 |
| 账本管理 | 全平台账本查询，按名称与类型筛选 |
| 交易流水 | 按账本、类型、时间区间、备注多维筛选 |

## 结构

```
src/
├── api/        axios 封装（统一鉴权、401 自动跳登录）与接口定义
├── stores/     Pinia 状态（登录态持久化）
├── router/     路由与登录守卫
├── layouts/    主布局（侧边栏 + 顶栏 + 用户菜单）
├── views/      各业务页面
├── types/      接口类型与金额格式化工具
├── styles/     全局样式
└── main.ts     应用入口（注册 Element Plus 与图标）
```

## 约定

- 后端返回的 ID 是**字符串**（避免 JS 精度丢失），前端一律按字符串处理
- 后端统一响应体为 `{ code, message, data, traceId }`，`code === 0` 表示成功
- 未认证时后端返回 **HTTP 401**（而非业务码），由 axios 的 error 分支统一处理跳转
