# quiz-ai-proxy（Cloudflare Worker）

隐藏智谱 API Key，为静态站点提供默认 AI 能力（国内直连）。

## 架构

```
浏览器 → Worker（/v1/chat/completions）→ 智谱 GLM API
         ↑ Key 在 Secrets，不暴露
```

- **默认模型**：`glm-4.7-flash`（官网免费）
- **学情分析**：非流式，OpenAI 兼容接口
- **AI 解析**：流式，OpenAI 兼容接口
- **429**：不重试，避免配额雪崩

## 部署

### Cloudflare Dashboard

1. Workers → 更新 `ai-proxy.js` → Deploy
2. Settings → Variables and Secrets → Add `ZHIPU_API_KEY`（[智谱开放平台](https://open.bigmodel.cn/usercenter/apikeys) 申请）
3. **建议**绑定 KV `AI_RATE` 启用 IP 限流（见 `wrangler.toml`）；未绑时用 Cache API 兜底
4. 在 `ALLOWED_ORIGINS` 中加入你的 GitHub Pages 域名（非白名单 Origin 一律 403）

### Wrangler CLI

```bash
cd workers
wrangler secret put ZHIPU_API_KEY
wrangler deploy
```

## 配置

| 项 | 说明 |
|----|------|
| `ZHIPU_API_KEY` | Secret，智谱 API Key |
| `ALLOWED_ORIGINS` | 允许跨域的前端域名 |
| `AI_RATE` | KV 命名空间，**建议绑定**；未绑时用 Cache API 兜底限流 |
| `HOURLY_LIMIT` | 每 IP 每小时请求上限（默认 40） |
| `DEFAULT_MODEL` | 默认 `glm-4.7-flash` |

部署完成后，将 Worker URL 写入项目根目录 `js/ai-config.js`：

```js
window.QuizAIConfig = {
  proxyUrl: 'https://quiz-ai-proxy.你的子域.workers.dev/v1',
  proxyModel: 'glm-4.7-flash',
};
```

## 注意

- **不要**用 ChatAnywhere Key 配置本 Worker（ChatAnywhere 禁止 Cloudflare 反向代理）
- ChatAnywhere 仅能通过项目内 `scripts/ai_proxy.py` 在本机使用
