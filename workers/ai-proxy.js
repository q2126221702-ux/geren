/**
 * Cloudflare Worker：隐藏 API Key，转发到智谱 GLM（OpenAI 兼容接口）。
 * 国内直连，默认模型 glm-4-flash（官网永久免费，RPM 更宽松）。
 *
 * 部署：Settings → Secrets → ZHIPU_API_KEY
 */

const UPSTREAM = 'https://open.bigmodel.cn/api/paas/v4/chat/completions';
const DEFAULT_MODEL = 'glm-4-flash';

const HOURLY_LIMIT = 25;

const ALLOWED_ORIGINS = new Set([
  'https://q2126221702-ux.github.io',
  'http://localhost:8080',
  'http://127.0.0.1:8080',
]);

export default {
  async fetch(request, env) {
    const origin = request.headers.get('Origin') || '';
    const cors = buildCors(origin);

    if (request.method === 'OPTIONS') {
      if (!ALLOWED_ORIGINS.has(origin)) {
        return new Response(null, { status: 403 });
      }
      return new Response(null, { status: 204, headers: cors });
    }

    if (request.method !== 'POST') {
      return json({ error: 'Method Not Allowed' }, 405, cors);
    }

    const pathname = new URL(request.url).pathname;
    if (!pathname.endsWith('/chat/completions')) {
      return json({ error: 'Not Found' }, 404, cors);
    }

    if (!ALLOWED_ORIGINS.has(origin)) {
      return json({ error: 'Origin not allowed' }, 403, cors);
    }

    const apiKey = env.ZHIPU_API_KEY;
    if (!apiKey) {
      return json(
        { error: { message: 'Worker 未配置 ZHIPU_API_KEY，请在 Settings → Secrets 添加智谱 API Key' } },
        500,
        cors
      );
    }

    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    const limited = await rateLimit(env, ip);
    if (!limited.ok) {
      return json(
        { error: { message: `站点 AI 配额繁忙，请 ${limited.retryMin} 分钟后再试` } },
        429,
        cors
      );
    }

    let payload;
    try {
      payload = JSON.parse(await request.text());
    } catch {
      return json({ error: { message: 'Invalid JSON body' } }, 400, cors);
    }

    normalizePayload(payload);

    return forwardToZhipu(payload, apiKey, cors);
  },
};

function normalizePayload(payload) {
  const model = String(payload.model || '').trim();
  if (!model || !model.startsWith('glm-')) {
    payload.model = DEFAULT_MODEL;
  }
  if (payload.stream !== true) {
    payload.stream = false;
  }
  // GLM-4.7 默认强制思考，站点代理需关闭否则短请求会极慢或看似卡死
  if (String(payload.model).startsWith('glm-4.7')) {
    payload.thinking = { type: 'disabled' };
  }
  const want = Number(payload.max_tokens) || 0;
  payload.max_tokens = Math.max(want, 256);
}

async function forwardToZhipu(payload, apiKey, cors) {
  let upstream;
  try {
    upstream = await fetch(UPSTREAM, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(35000),
    });
  } catch (err) {
    if (err && err.name === 'TimeoutError') {
      return json({ error: { message: '智谱 API 响应超时，请稍后再试' } }, 504, cors);
    }
    return json({ error: { message: '无法连接智谱 API 上游' } }, 502, cors);
  }

  const resHeaders = new Headers(cors);
  const ct = upstream.headers.get('Content-Type');
  if (ct) resHeaders.set('Content-Type', ct);

  if (payload.stream === true && upstream.ok && upstream.body) {
    return new Response(upstream.body, { status: upstream.status, headers: resHeaders });
  }

  const body = await upstream.text();
  if (!ct) resHeaders.set('Content-Type', 'application/json');
  return new Response(body, { status: upstream.status, headers: resHeaders });
}

function buildCors(origin) {
  const headers = {
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Max-Age': '86400',
  };
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    headers['Access-Control-Allow-Origin'] = origin;
  }
  return headers;
}

function json(data, status, cors) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...cors, 'Content-Type': 'application/json' },
  });
}

async function rateLimit(env, ip) {
  const hour = Math.floor(Date.now() / 3600000);
  const key = `rl:${ip}:${hour}`;
  let count;

  if (env.AI_RATE) {
    count = Number((await env.AI_RATE.get(key)) || '0') + 1;
    if (count > HOURLY_LIMIT) {
      const retryMin = Math.ceil((hour + 1) * 3600000 - Date.now()) / 60000;
      return { ok: false, retryMin: Math.max(1, Math.round(retryMin)) };
    }
    await env.AI_RATE.put(key, String(count), { expirationTtl: 7200 });
    return { ok: true };
  }

  const cache = caches.default;
  const cacheKey = new Request(`https://rate-limit.internal/${key}`);
  const hit = await cache.match(cacheKey);
  count = Number(hit ? await hit.text() : '0') + 1;
  if (count > HOURLY_LIMIT) {
    const retryMin = Math.ceil((hour + 1) * 3600000 - Date.now()) / 60000;
    return { ok: false, retryMin: Math.max(1, Math.round(retryMin)) };
  }
  await cache.put(
    cacheKey,
    new Response(String(count), { headers: { 'Cache-Control': 'max-age=7200' } })
  );
  return { ok: true };
}
