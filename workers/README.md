# quiz-ai-proxy（Cloudflare Worker）

隐藏 Gemini API Key，为静态站点提供默认 AI 能力。

## 架构

```
浏览器 → Worker（/v1/chat/completions）→ Google Gemini API
         ↑ Key 在 Secrets，不暴露
```

- **学情分析**：非流式，走 Gemini 原生 `generateContent`（更稳定）
- **AI 解析**：流式，走 OpenAI 兼容接口
- **429**：不重试多个模型，避免配额雪崩

## 部署

### Cloudflare Dashboard

1. Workers → Create → 粘贴 `ai-proxy.js` → Deploy
2. Settings → Variables and Secrets → Add `GEMINI_API_KEY`（AI Studio 免费 Key）
3. **建议**绑定 KV `AI_RATE` 启用 IP 限流（见 `wrangler.toml`）；未绑时用 Cache API 兜底
4. 在 `ALLOWED_ORIGINS` 中加入你的 GitHub Pages 域名（非白名单 Origin 一律 403）

### Wrangler CLI

```bash
cd workers
wrangler secret put GEMINI_API_KEY
wrangler deploy
```

## 配置

| 项 | 说明 |
|----|------|
| `GEMINI_API_KEY` | Secret，Gemini API Key |
| `ALLOWED_ORIGINS` | 允许跨域的前端域名 |
| `AI_RATE` | KV 命名空间，**建议绑定**；未绑时用 Cache API 兜底限流 |
| `HOURLY_LIMIT` | 每 IP 每小时请求上限（默认 40） |
| `DEFAULT_MODEL` | 默认 `gemini-2.0-flash-lite` |

部署完成后，将 Worker URL 写入项目根目录 `js/ai-config.js`：

```js
window.QuizAIConfig = {
  proxyUrl: 'https://quiz-ai-proxy.你的子域.workers.dev/v1',
  proxyModel: 'gemini-2.0-flash-lite',
};
```

## 注意

- **不要**用 ChatAnywhere Key 配置本 Worker（ChatAnywhere 禁止 Cloudflare 反向代理）
- ChatAnywhere 仅能通过项目内 `scripts/ai_proxy.py` 在本机使用
