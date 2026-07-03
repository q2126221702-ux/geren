/**
 * Cloudflare Worker：隐藏 API Key，转发到 Gemini。
 * 非流式走原生 generateContent（更稳定）；流式走 OpenAI 兼容接口。
 *
 * 部署：Settings → Secrets → GEMINI_API_KEY
 */

const OPENAI_UPSTREAM =
  'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions';
const DEFAULT_MODEL = 'gemini-2.0-flash-lite';

const HOURLY_LIMIT = 40;

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

    const apiKey = env.GEMINI_API_KEY;
    if (!apiKey) {
      return json(
        { error: { message: 'Worker 未配置 GEMINI_API_KEY，请在 Settings → Secrets 添加' } },
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

    if (!payload.model || String(payload.model).startsWith('gpt-')) {
      payload.model = DEFAULT_MODEL;
    }

    const wantStream = payload.stream === true;

    if (!wantStream) {
      payload.stream = false;
      return handleNativeGenerate(payload, apiKey, cors);
    }

    return handleOpenAiStream(payload, apiKey, cors);
  },
};

/** 非流式：原生 Gemini API，一次请求、不重试，避免 429 雪崩 */
async function handleNativeGenerate(payload, apiKey, cors) {
  const model = payload.model || DEFAULT_MODEL;
  const prompt = messagesToPrompt(payload.messages);
  const maxOutputTokens = Math.max(Number(payload.max_tokens) || 0, 1024);

  const url =
    `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}` +
    `:generateContent?key=${encodeURIComponent(apiKey)}`;

  let upstream;
  try {
    upstream = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: {
          temperature: payload.temperature ?? 0.5,
          maxOutputTokens,
        },
      }),
    });
  } catch {
    return json({ error: { message: '无法连接 Gemini 上游' } }, 502, cors);
  }

  const bodyText = await upstream.text();

  if (!upstream.ok) {
    return new Response(bodyText, {
      status: upstream.status,
      headers: { ...cors, 'Content-Type': 'application/json' },
    });
  }

  let data;
  try {
    data = JSON.parse(bodyText);
  } catch {
    return json({ error: { message: 'Gemini 返回格式异常' } }, 502, cors);
  }

  const candidate = data.candidates?.[0];
  const content =
    candidate?.content?.parts?.map((p) => p.text || '').join('') || '';
  const finishReason = candidate?.finishReason || 'STOP';

  const openAiBody = {
    id: 'chatcmpl-proxy',
    object: 'chat.completion',
    choices: [
      {
        index: 0,
        message: { role: 'assistant', content },
        finish_reason: finishReason === 'MAX_TOKENS' ? 'length' : 'stop',
      },
    ],
  };

  return json(openAiBody, 200, cors);
}

/** 流式：OpenAI 兼容接口，仅单模型，429 不重试 */
async function handleOpenAiStream(payload, apiKey, cors) {
  payload.model = payload.model || DEFAULT_MODEL;
  payload.stream = true;

  const upstream = await fetch(OPENAI_UPSTREAM, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify(payload),
  });

  const resHeaders = new Headers(cors);
  const ct = upstream.headers.get('Content-Type');
  if (ct) resHeaders.set('Content-Type', ct);

  if (upstream.ok && upstream.body) {
    return new Response(upstream.body, { status: upstream.status, headers: resHeaders });
  }

  const body = await upstream.text();
  resHeaders.set('Content-Type', 'application/json');
  return new Response(body, { status: upstream.status, headers: resHeaders });
}

function messagesToPrompt(messages) {
  if (!Array.isArray(messages)) return '';
  return messages
    .map((m) => {
      const role = m.role === 'assistant' ? '助手' : m.role === 'system' ? '系统' : '用户';
      return `${role}：${m.content || ''}`;
    })
    .join('\n\n');
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

  // 未绑 KV 时用 Cache API 兜底（按 IP 限流，建议生产环境仍绑定 AI_RATE KV）
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
