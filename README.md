# 在线测验

静态网页题库，支持单选、多选（高考数学判分规则）、判断、填空、问答等题型。

## 本地运行

```bat
start.bat
```

浏览器打开 http://localhost:8080

## 题库

题目数据在 `data/` 目录，索引见 `data/manifest.json`。

当前包含：

- 工业通信类测验（Profinet / OPC / MODBUS / 串口 / 综合考核）
- WE Learn 实用综合教程 B1U4–U8 翻译练习

## AI 功能

| 功能 | 说明 |
|------|------|
| AI 解析 | 复习模式下，逐题生成讲解 |
| AI 学情分析 | 交卷后，根据得分与错题生成学习建议 |
| AI 设置 | 用户自带 API Key（BYOK），保存在本机浏览器 |

### 站点默认 AI（免费）

未填写自带 Key 时，使用 **Cloudflare Worker → Google Gemini**（`gemini-2.0-flash-lite`）：

- API Key 存在 Worker Secrets（`GEMINI_API_KEY`），**不会**出现在前端或 Git 仓库
- 前端配置见 `js/ai-config.js`（仅 Worker 地址，无 Key）
- 共享免费配额，请勿频繁点击；用户可在设置页填写自己的 Gemini Key 绕过 Worker

### 自带 Key（BYOK）· 完整模式

在「AI 设置」填写 API Key 并保存后，自动进入**完整模式**（直连服务商，不经过 Worker）：

| 能力 | 完整模式（自带 Key） | 站点默认 AI |
|------|---------------------|-------------|
| 学情分析 | 流式、350–600 字、含全部错题 | 精简、非流式 |
| 重新分析 | 支持 | 仅查看缓存 |
| 点击冷却 | 无 | 90 秒 |
| 测试连接冷却 | 无 | 15 秒 |

支持 Gemini、DeepSeek、Moonshot/Kimi、智谱、通义千问、硅基流动、火山引擎/豆包、GitHub Models、ChatAnywhere 等。清除 Key 后恢复站点默认 AI。

使用 AI 时，题目与作答会发送至对应 AI 服务商处理；API Key 与加密口令不会上传至本站。

### 部署 Cloudflare Worker

1. 在 [Google AI Studio](https://aistudio.google.com/apikey) 申请 Gemini API Key
2. Cloudflare Dashboard → Workers → 创建 Worker，粘贴 `workers/ai-proxy.js`
3. Settings → Secrets → 添加 `GEMINI_API_KEY`
4. 将 Worker 地址写入 `js/ai-config.js` 的 `proxyUrl`（形如 `https://xxx.workers.dev/v1`）
5. 在 Worker 代码 `ALLOWED_ORIGINS` 中加入你的 GitHub Pages 域名（仅白名单 Origin 可调用，拒绝 curl/脚本直调）

6. **建议**绑定 KV namespace `AI_RATE` 启用 IP  hourly 限流（见 `workers/wrangler.toml`）；未绑 KV 时会用 Cache API 兜底限流

### 本地可选：ChatAnywhere 代理

若需在本机用 ChatAnywhere（**不能**经 Cloudflare 转发）：

```bat
copy .env.example .env
:: 编辑 .env 填入 CHATANYWHERE_API_KEY
copy js\ai-config.local.example.js js\ai-config.local.js
:: 编辑 ai-config.local.js 启用 localhost 代理
start-ai.bat
```

日常本地调试可直接 `start.bat`，与线上一致使用 Worker；或在 `ai-config.local.js` 中设 `proxyUrl: ''` 后只用自己的 Gemini Key。

### 勿提交到 Git

以下已在 `.gitignore` 中：

- `.env`（本地 ChatAnywhere Key）
- `js/ai-config.local.js`（本地覆盖配置）

## 测试

需先启动本地服务器，再运行：

```bat
python scripts/test_full.py
```

## GitHub Pages

推送到 `main` 分支后，Actions 会自动部署。

在线访问：https://q2126221702-ux.github.io/geren/
