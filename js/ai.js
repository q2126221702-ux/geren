(function () {
  'use strict';

  const STORAGE_KEY = 'quiz-ai-settings';
  let unlockedApiKey = '';

  const PROVIDERS = {
    gemini: {
      name: 'Google Gemini',
      baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
      defaultModel: 'gemini-2.0-flash-lite',
      fallbackModels: [
        'gemini-2.0-flash-lite',
        'gemini-2.5-flash',
        'gemini-2.0-flash',
        'gemini-1.5-flash-8b',
      ],
      keyUrl: 'https://aistudio.google.com/apikey',
      hint: '在 Google AI Studio 申请 API Key；默认用轻量模型 gemini-2.0-flash-lite。国内可能需要科学上网',
    },
    deepseek: {
      name: 'DeepSeek',
      baseUrl: 'https://api.deepseek.com/v1',
      defaultModel: 'deepseek-v4-flash',
      keyUrl: 'https://platform.deepseek.com/api_keys',
      hint: 'V4：deepseek-v4-flash（快）/ deepseek-v4-pro（强）。已开启思考模式，页面仅显示最终答案（不展示推理过程）',
    },
    moonshot: {
      name: 'Moonshot / Kimi',
      baseUrl: 'https://api.moonshot.cn/v1',
      defaultModel: 'moonshot-v1-8k',
      keyUrl: 'https://platform.moonshot.cn/console/api-keys',
      hint: '月之暗面 Kimi API；常用 moonshot-v1-8k / moonshot-v1-32k / moonshot-v1-auto',
    },
    zhipu: {
      name: '智谱 AI',
      baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
      defaultModel: 'glm-4.7-flash',
      keyUrl: 'https://open.bigmodel.cn/usercenter/apikeys',
      hint: 'glm-4.7-flash 官网完全免费（推荐）；亦可用 glm-4-flash / glm-4-air / glm-4-plus',
    },
    dashscope: {
      name: '阿里云通义',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      defaultModel: 'qwen-turbo',
      keyUrl: 'https://bailian.console.aliyun.com/?tab=model#/api-key',
      hint: 'DashScope 兼容 OpenAI 接口；常用 qwen-turbo / qwen-plus / qwen-max',
    },
    siliconflow: {
      name: '硅基流动',
      baseUrl: 'https://api.siliconflow.cn/v1',
      defaultModel: 'deepseek-ai/DeepSeek-V3',
      keyUrl: 'https://cloud.siliconflow.cn/account/ak',
      hint: '国内常用模型聚合；模型名带厂商前缀，如 deepseek-ai/DeepSeek-V3、Qwen/Qwen2.5-72B-Instruct',
    },
    volcengine: {
      name: '火山引擎 / 豆包',
      baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
      defaultModel: 'doubao-1-5-pro-32k-250115',
      keyUrl: 'https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey',
      hint: '豆包模型；若使用推理接入点，请将模型名改为控制台中的 Endpoint ID（ep- 开头）',
    },
    github: {
      name: 'GitHub Models',
      baseUrl: 'https://models.github.ai/inference',
      defaultModel: 'openai/gpt-4o-mini',
      keyUrl: 'https://github.com/settings/tokens',
      hint: 'Personal Access Token 需勾选 models:read 权限',
    },
    chatanywhere: {
      name: 'ChatAnywhere 中转',
      baseUrl: 'https://api.chatanywhere.tech/v1',
      defaultModel: 'gpt-4o-mini',
      keyUrl: 'https://api.chatanywhere.tech/v1/oauth/free/render',
      hint: 'GitHub 登录领取免费 Key，国内访问较快',
    },
  };

  function b64Encode(buf) {
    const bytes = new Uint8Array(buf);
    let s = '';
    for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return btoa(s);
  }

  function b64Decode(str) {
    const bin = atob(str);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return bytes;
  }

  function readRawSettings() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  function writeRawSettings(data) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  }

  function removeRawSettings() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }

  function isStorageEmpty() {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return true;
    try {
      const data = JSON.parse(raw);
      const hasKey =
        Boolean(data?.keyEnc) || String(data?.apiKey || '').trim().length > 0;
      const hasModel = String(data?.model || '').trim().length > 0;
      return !hasKey && !hasModel;
    } catch {
      return false;
    }
  }

  async function deriveAesKey(passphrase, salt) {
    const enc = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
      'raw',
      enc.encode(passphrase),
      'PBKDF2',
      false,
      ['deriveKey']
    );
    return crypto.subtle.deriveKey(
      { name: 'PBKDF2', salt, iterations: 120000, hash: 'SHA-256' },
      keyMaterial,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
  }

  async function encryptApiKey(apiKey, passphrase) {
    const enc = new TextEncoder();
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const aesKey = await deriveAesKey(passphrase, salt);
    const ciphertext = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      aesKey,
      enc.encode(apiKey)
    );
    return {
      v: 1,
      salt: b64Encode(salt),
      iv: b64Encode(iv),
      data: b64Encode(ciphertext),
    };
  }

  async function decryptApiKey(keyEnc, passphrase) {
    const salt = b64Decode(keyEnc.salt);
    const iv = b64Decode(keyEnc.iv);
    const data = b64Decode(keyEnc.data);
    const aesKey = await deriveAesKey(passphrase, salt);
    const plainBuf = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, aesKey, data);
    return new TextDecoder().decode(plainBuf);
  }

  function loadSettings() {
    const data = readRawSettings();
    if (!data) {
      return {
        provider: 'gemini',
        model: '',
        apiKey: '',
        keyStored: false,
        keyEncrypted: false,
        keyUnlocked: false,
      };
    }
    const provider = PROVIDERS[data.provider] ? data.provider : 'gemini';
    const model = String(data.model || '');
    const keyEncrypted = Boolean(data.keyEnc);
    const plainKey = String(data.apiKey || '');
    const keyStored = keyEncrypted || plainKey.length > 0;
    const keyUnlocked = keyEncrypted ? unlockedApiKey.length > 0 : plainKey.length > 0;
    return {
      provider,
      model,
      apiKey: keyEncrypted ? '' : plainKey,
      keyStored,
      keyEncrypted,
      keyUnlocked,
    };
  }

  function getApiKey() {
    const data = readRawSettings();
    if (!data) return '';
    if (data.keyEnc) return unlockedApiKey;
    return String(data.apiKey || '').trim();
  }

  function hasStoredKey() {
    const data = readRawSettings();
    if (!data) return false;
    return Boolean(data.keyEnc) || String(data.apiKey || '').trim().length > 0;
  }

  function isKeyUnlocked() {
    if (!hasStoredKey()) return false;
    const data = readRawSettings();
    if (data?.keyEnc) return unlockedApiKey.length > 0;
    return true;
  }

  function canUseOwnKey() {
    return hasStoredKey() && isKeyUnlocked();
  }

  async function saveSettings(settings) {
    const provider = PROVIDERS[settings.provider] ? settings.provider : 'gemini';
    const model = (settings.model || '').trim();
    const apiKey = (settings.apiKey || '').trim();
    const encryptPassphrase = (settings.encryptPassphrase || '').trim();
    const existing = readRawSettings();

    if (apiKey && encryptPassphrase) {
      const keyEnc = await encryptApiKey(apiKey, encryptPassphrase);
      writeRawSettings({ provider, model, keyEnc, savedAt: Date.now() });
      unlockedApiKey = apiKey;
      return;
    }

    if (apiKey) {
      writeRawSettings({ provider, model, apiKey, savedAt: Date.now() });
      unlockedApiKey = apiKey;
      return;
    }

    if (existing?.keyEnc) {
      writeRawSettings({ provider, model, keyEnc: existing.keyEnc, savedAt: existing.savedAt || Date.now() });
      return;
    }

    if (existing?.apiKey) {
      writeRawSettings({
        provider,
        model,
        apiKey: existing.apiKey,
        savedAt: existing.savedAt || Date.now(),
      });
      unlockedApiKey = String(existing.apiKey).trim();
      return;
    }

    writeRawSettings({ provider, model, apiKey: '', savedAt: 0 });
    unlockedApiKey = '';
  }

  async function unlockKey(passphrase) {
    const data = readRawSettings();
    if (!data?.keyEnc) {
      throw new Error('当前未保存加密 Key');
    }
    if (!passphrase) {
      throw new Error('请输入解锁口令');
    }
    try {
      unlockedApiKey = await decryptApiKey(data.keyEnc, passphrase);
      return true;
    } catch {
      unlockedApiKey = '';
      throw new Error('解锁口令错误');
    }
  }

  function lockKey() {
    unlockedApiKey = '';
  }

  function getStoredKeyHint() {
    const data = readRawSettings();
    if (!data) return '';
    if (data.keyEnc) {
      return isKeyUnlocked() ? 'Key 已加密保存并已解锁' : 'Key 已加密保存在本机';
    }
    const key = String(data.apiKey || '').trim();
    if (!key) return '';
    if (key.length <= 8) return 'Key 已保存在本机浏览器';
    return `Key 已保存在本机：${key.slice(0, 4)}****${key.slice(-4)}`;
  }

  function clearSavedCredentials() {
    unlockedApiKey = '';
    removeRawSettings();
    try {
      localStorage.setItem(STORAGE_KEY, '');
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
    return !hasStoredKey() && unlockedApiKey === '' && isStorageEmpty();
  }

  function isFullyCleared() {
    return !hasStoredKey() && unlockedApiKey === '' && isStorageEmpty();
  }

  function isConfigured() {
    if (canUseOwnKey()) return true;
    if (isProxyAvailable()) return true;
    return false;
  }

  function isMobileClient() {
    return /Android|iPhone|iPad|Mobile/i.test(navigator.userAgent || '');
  }

  function getProxyConfigList() {
    const c = window.QuizAIConfig || {};
    let urls = Array.isArray(c.proxyUrls)
      ? c.proxyUrls.slice()
      : c.proxyUrl
        ? [c.proxyUrl]
        : [];
    urls = urls.map((u) => String(u || '').trim().replace(/\/$/, '')).filter(Boolean);
    const model = String(c.proxyModel || 'glm-4-flash').trim();
    return urls.map((baseUrl) => ({
      baseUrl,
      model,
      provider: 'zhipu',
      apiKey: '',
    }));
  }

  function getProxyConfig() {
    const list = getProxyConfigList();
    return list.length ? list[0] : null;
  }

  function isProxyAvailable() {
    return getProxyConfigList().length > 0;
  }

  function isProxyNetworkError(err) {
    const msg = String(err?.message || err || '');
    return /无法连接|Failed to fetch|NetworkError|Load failed|network/i.test(msg);
  }

  function usesProxy() {
    return isProxyAvailable() && !canUseOwnKey();
  }

  function hasOwnKey() {
    return canUseOwnKey();
  }

  function getAiMode() {
    if (canUseOwnKey()) return 'own-key';
    if (hasStoredKey() && !isKeyUnlocked()) return 'locked';
    if (isProxyAvailable()) return 'proxy';
    return 'none';
  }

  function getAiSourceLabel() {
    if (canUseOwnKey()) {
      const name = getProviderInfo(loadSettings().provider).name;
      const enc = loadSettings().keyEncrypted ? ' · 口令加密' : ' · 本机已保存';
      return `自带 Key · 完整模式（${name}${enc}）`;
    }
    if (hasStoredKey() && !isKeyUnlocked()) {
      return '自带 Key · 已加密保存（待解锁）';
    }
    if (isProxyAvailable()) return '站点默认 AI（Cloudflare → 智谱 GLM-4-Flash，有配额限制）';
    return '未配置';
  }

  function getProviderInfo(providerId) {
    return PROVIDERS[providerId] || PROVIDERS.gemini;
  }

  function getActiveModel(settings) {
    const p = getProviderInfo(settings.provider);
    return settings.model || p.defaultModel;
  }

  function parseApiError(status, body, providerId) {
    let msg = body;
    let detail = '';
    try {
      const j = JSON.parse(body);
      const err = j.error;
      if (typeof err === 'object' && err !== null) {
        detail = err.message || err.msg || '';
      } else if (typeof err === 'string') {
        detail = err;
      }
      msg = detail || j.message || body;
    } catch {
      detail = String(body).slice(0, 200);
    }
    if (status === 401) {
      return `API Key 无效或已过期（${getProviderInfo(providerId).name}）`;
    }
    if (status === 429) {
      if (detail.includes('站点 AI 配额')) {
        return detail;
      }
      const tips = {
        gemini: usesProxy()
          ? '站点共享 Gemini 配额已用尽。请在「AI 设置」填写自己的 Gemini Key，或等明天配额重置。'
          : 'Gemini 免费配额限制。请等 1–2 分钟再试；若仍失败，可到 Google AI Studio 查看用量',
        deepseek: 'DeepSeek 账户请求过快或余额不足，请稍后重试',
        moonshot: 'Moonshot 请求过快或余额不足，请稍后重试',
        zhipu: usesProxy()
          ? '站点共享智谱 Key 触发速率限制（免费 Key 有 RPM 上限）。请等 1–2 分钟再试，或在「AI 设置」填写自己的智谱 Key 启用完整模式。'
          : '智谱 API 请求过快或余额不足，请稍后重试',
        dashscope: '通义千问配额或 RPM 限制，请稍后重试',
        siliconflow: '硅基流动请求过快或余额不足，请稍后重试',
        volcengine: '火山引擎配额或 RPM 限制，请稍后重试',
        github: 'GitHub Models 免费 RPM 较低，请等 1 分钟再试',
        chatanywhere: 'ChatAnywhere 免费 Key 每日次数有限；若要用站点默认 AI，请在设置里清除自带 Key',
      };
      const hint = tips[providerId] || '请稍后再试';
      const extra = detail ? `（${String(detail).slice(0, 160)}）` : '';
      return `请求过于频繁 / 配额已用尽${extra}。${hint}`;
    }
    if (status === 403) return `无权限访问：${String(msg).slice(0, 150)}`;
    return String(msg).slice(0, 300) || `请求失败 (${status})`;
  }

  /** 去掉可能混在 content 里的思考标签（中转/本地模型常见） */
  function stripThinkingFromText(text) {
    let result = String(text || '');
    const patterns = [
      /<think[^>]*>[\s\S]*?<\/think>/gi,
      /<redacted_reasoning[^>]*>[\s\S]*?<\/redacted_reasoning>/gi,
      /<redacted_thinking[^>]*>[\s\S]*?<\/redacted_thinking>/gi,
    ];
    for (const re of patterns) {
      result = result.replace(re, '');
    }
    result = result
      .replace(/<think[^>]*>[\s\S]*/gi, '')
      .replace(/<redacted_reasoning[^>]*>[\s\S]*/gi, '')
      .replace(/<redacted_thinking[^>]*>[\s\S]*/gi, '');
    return result.trim();
  }

  /** 只取最终答案：忽略 reasoning_content，并清理 content 内嵌思考 */
  function extractFinalAnswer(message) {
    const content = message?.content;
    if (typeof content === 'string') {
      return stripThinkingFromText(content);
    }
    return '';
  }

  /** 部分代理仍返回 SSE，合并为完整文本 */
  function parseSseContent(raw) {
    let full = '';
    for (const line of String(raw || '').split('\n')) {
      const trimmed = line.trim();
      if (!trimmed.startsWith('data:')) continue;
      const payload = trimmed.slice(5).trim();
      if (payload === '[DONE]') continue;
      try {
        const json = JSON.parse(payload);
        full += json.choices?.[0]?.delta?.content || json.choices?.[0]?.message?.content || '';
      } catch {
        /* skip */
      }
    }
    return stripThinkingFromText(full);
  }

  async function readChatResponse(res) {
    const ct = res.headers.get('Content-Type') || '';
    const raw = await res.text();
    if (ct.includes('text/event-stream') || raw.trimStart().startsWith('data:')) {
      const text = parseSseContent(raw);
      if (text) return text;
    }
    try {
      const data = JSON.parse(raw);
      return extractFinalAnswer(data.choices?.[0]?.message);
    } catch {
      return stripThinkingFromText(raw);
    }
  }

  /** 检测明显未写完的回复（半截句子） */
  function looksIncomplete(text) {
    const t = String(text || '').trim();
    if (t.length < 50) return true;
    if (/[。！？…」】）\n]$/.test(t)) return false;
    if (t.length >= 220) return false;
    if (/[，、：；「【（]$/.test(t)) return true;
    if (/[\u4e00-\u9fff]$/.test(t) && t.length < 180) {
      const lastClause = t.split(/[。！？\n]/).pop() || '';
      if (lastClause.length > 0 && lastClause.length < 40 && !/[。！？]$/.test(t)) return true;
    }
    return false;
  }

  function applyDeepSeekRequestOptions(payload, options) {
    payload.thinking = { type: 'enabled' };
    const want = options?.maxTokens || 4096;
    const budget = Math.max(want + 2048, 4096);
    payload.max_tokens = budget;
    payload.max_completion_tokens = budget;
  }

  /** GLM-4.7 默认强制思考，测验场景关闭以加快响应 */
  function applyZhipuRequestOptions(payload, options) {
    const model = String(payload.model || '');
    if (model.startsWith('glm-4.7')) {
      payload.thinking = { type: 'disabled' };
    }
    const want = options?.maxTokens || 512;
    payload.max_tokens = Math.max(want, 64);
  }

  /** 从 SSE delta 只取最终答案字段，忽略 reasoning_content */
  function extractStreamContentDelta(delta) {
    if (!delta || typeof delta !== 'object') return '';
    return delta.content || '';
  }

  async function openAiCompatibleChat(ctx, messages, onChunk, options) {
    const url = `${ctx.baseUrl}/chat/completions`;
    const wantStream = options?.stream === true || (Boolean(onChunk) && options?.stream !== false);
    const payload = {
      model: ctx.model,
      messages,
      stream: wantStream,
      temperature: options?.temperature ?? 0.6,
    };
    if (options?.maxTokens) {
      payload.max_tokens = options.maxTokens;
      payload.max_completion_tokens = options.maxTokens;
    }
    if (ctx.provider === 'deepseek') applyDeepSeekRequestOptions(payload, options);
    if (ctx.provider === 'zhipu') applyZhipuRequestOptions(payload, options);

    const headers = { 'Content-Type': 'application/json' };
    if (ctx.apiKey) headers.Authorization = `Bearer ${ctx.apiKey}`;

    const timeoutMs = options?.timeoutMs ?? (ctx.apiKey ? 120000 : 120000);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    let res;
    let lastErr;
    try {
      for (let attempt = 0; attempt < 2; attempt++) {
        if (attempt > 0) {
          await new Promise((r) => setTimeout(r, 1500));
        }
        try {
          res = await fetch(url, {
            method: 'POST',
            headers,
            body: JSON.stringify(payload),
            mode: 'cors',
            credentials: 'omit',
            referrerPolicy: ctx.apiKey ? 'no-referrer' : 'origin',
            signal: controller.signal,
          });
          lastErr = null;
          break;
        } catch (err) {
          lastErr = err;
          if (attempt === 1 || (err && err.name === 'AbortError')) break;
        }
      }
      if (lastErr) {
        const err = lastErr;
        if (err && err.name === 'AbortError') {
          throw new Error(
            ctx.apiKey
              ? 'AI 请求超时，请检查网络或更换模型后重试。'
              : '连接站点 AI 代理超时。请清除浏览器缓存后重试，或在「AI 设置」填写智谱 Key 直连。'
          );
        }
        const hint = ctx.apiKey
          ? '无法连接 AI 服务，请检查网络或 API 设置。'
          : '无法连接 AI 代理（已尝试全部线路）。手机移动网络建议切换 WiFi，或在 AI 设置填写智谱 Key 直连。';
        throw new Error(hint);
      }
    } finally {
      clearTimeout(timer);
    }

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(parseApiError(res.status, errText, ctx.provider));
    }

    if (!wantStream) {
      return readChatResponse(res);
    }

    if (!onChunk) {
      return readChatResponse(res);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let full = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('data:')) continue;
        const payloadLine = trimmed.slice(5).trim();
        if (payloadLine === '[DONE]') continue;
        try {
          const json = JSON.parse(payloadLine);
          const delta = json.choices?.[0]?.delta || {};
          const piece = extractStreamContentDelta(delta);
          if (piece) {
            full += piece;
            onChunk(stripThinkingFromText(full));
          }
        } catch {
          /* skip malformed chunk */
        }
      }
    }
    return stripThinkingFromText(full);
  }

  function resolveChatContext(settings) {
    const apiKey = getApiKey();
    if (apiKey) {
      const provider = getProviderInfo(settings.provider);
      return {
        baseUrl: provider.baseUrl,
        apiKey,
        model: getActiveModel(settings),
        provider: settings.provider,
      };
    }
    const proxy = getProxyConfig();
    if (proxy) {
      return {
        baseUrl: proxy.baseUrl,
        apiKey: '',
        model: proxy.model,
        provider: proxy.provider,
      };
    }
    return null;
  }

  function resolveProxyContexts() {
    return getProxyConfigList();
  }

  async function chatCompletion(messages, onChunk, options) {
    const settings = loadSettings();
    const ownCtx = getApiKey()
      ? {
          baseUrl: getProviderInfo(settings.provider).baseUrl,
          apiKey: getApiKey(),
          model: getActiveModel(settings),
          provider: settings.provider,
        }
      : null;

    if (ownCtx) {
      return openAiCompatibleChat(ownCtx, messages, onChunk, options);
    }

    const proxies = resolveProxyContexts();
    if (!proxies.length) {
      if (hasStoredKey() && !isKeyUnlocked()) {
        throw new Error('Key 已口令加密，请先在 AI 设置中输入解锁口令');
      }
      throw new Error('请先在「AI 设置」中配置 API Key，或由管理员启用站点默认 AI');
    }

    let lastErr;
    for (let i = 0; i < proxies.length; i++) {
      try {
        return await openAiCompatibleChat(proxies[i], messages, onChunk, options);
      } catch (err) {
        lastErr = err;
        if (i < proxies.length - 1 && isProxyNetworkError(err)) continue;
        throw err;
      }
    }
    throw lastErr || new Error('无法连接 AI 代理');
  }

  function formatAiHtml(text) {
    return String(text || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>');
  }

  function buildQuestionPrompt(q, userAnswer, gradeResult, options) {
    const full = options && options.full;
    const parts = [
      full
        ? '你是一位经验丰富的老师，请用中文为学生做**深度讲解**。'
        : '你是一位耐心的老师，请用中文为学生**简要讲解**这道测验题。',
      full
        ? '要求：条理清晰、由浅入深；翻译题需分析语法/词汇/搭配并给例句；技术题需解释原理并联系应用场景；可补充易混点与同类考点。'
        : '要求：简洁明了、直击要点；翻译题说明关键语法/词汇；技术题解释核心概念；不要复述整题题干。',
      '',
      `题型：${q.type}`,
      `题目：${q.title}`,
    ];

    if (q.options?.length) {
      parts.push('选项：');
      q.options.forEach((opt, i) => {
        parts.push(`${String.fromCharCode(65 + i)}. ${opt}`);
      });
    }

    parts.push(`参考答案：${q.correct_answer}`);

    const user = String(userAnswer || '').trim();
    if (user) {
      parts.push(`学生作答：${user}`);
    } else {
      parts.push('学生作答：（未作答）');
    }

    if (gradeResult && gradeResult.correct === true) {
      parts.push('判题结果：正确');
    } else if (gradeResult && gradeResult.partial) {
      parts.push(`判题结果：部分正确（${gradeResult.score}/${gradeResult.maxScore} 分）`);
    } else if (gradeResult && gradeResult.correct === false) {
      parts.push('判题结果：错误');
    } else if (q.type === '问答题') {
      parts.push('判题结果：问答题（请对照参考答案点评学生译文/回答）');
    }

    if (full) {
      parts.push(
        '',
        '请按以下四部分输出（约 350–550 字）：',
        '1) **解题思路**：如何审题、如何一步步得出答案',
        '2) **正确答案要点**：逐点说明为什么选/写这些',
        '3) **错因与掌握**：答错或未作答时分析具体错因；答对则写「本题作答正确」并补充可提升之处',
        '4) **拓展巩固**：记忆口诀、易混点对比、**同类题联想**（见下规则）或一句话总结',
        '',
        '【同类题联想规则】若举相似问题，必须写成「问：…… → 答：……」格式，**直接给出简要答案**（关键词或短语即可）；禁止只点评“这是哪一层/哪种题型”而不写答案。示例：问「Profinet 应用层基于什么协议？」→ 答：RPC（远程过程调用）。'
      );
    } else {
      parts.push(
        '',
        '请按以下三部分**简短**输出（约 120–200 字，一段说完即可，不要小标题编号）：',
        '先写解题思路（1–2 句）→ 正确答案要点（1–2 句）→ 错因或掌握要点（1–2 句；答对则写「本题作答正确」）。',
        '不要拓展、不要列表、不要写学习态度类空话。'
      );
    }

    return parts.join('\n');
  }

  function buildResultAnalysisPrompt(quiz, answers, stats, options) {
    const full = options && options.full;
    const wrongItems = [];
    const typeCount = {};

    quiz.questions.forEach((q, i) => {
      if (q.type === '问答题') {
        wrongItems.push({
          no: i + 1,
          type: q.type,
          title: q.title.slice(0, full ? 120 : 40),
          status: '问答题',
        });
        typeCount[q.type] = (typeCount[q.type] || 0) + 1;
        return;
      }
      const result = stats.gradeFn(q, answers[i]);
      if (!result.correct) {
        wrongItems.push({
          no: i + 1,
          type: q.type,
          title: q.title.slice(0, full ? 120 : 40),
          status: result.partial ? '部分正确' : '错误',
        });
        typeCount[q.type] = (typeCount[q.type] || 0) + 1;
      }
    });

    const typeSummary = Object.entries(typeCount)
      .map(([t, n]) => `${t}${n}题`)
      .join('、');

    const scoreBlock = [
      `测验：${quiz.title}`,
      `客观题得分：${stats.score}/${stats.total}（得分率 ${stats.rate}%）`,
      `全对 ${stats.correctCount} 题，部分正确 ${stats.partialCount} 题，客观题共 ${stats.objectiveCount} 题`,
    ].join('\n');

    if (full) {
      const wrongLines = wrongItems.map(
        (w) => `第${w.no}题 [${w.type}·${w.status}] ${w.title}`
      );
      const wrongSection = wrongItems.length
        ? [`错题明细（共 ${wrongItems.length} 题${typeSummary ? `，${typeSummary}` : ''}）：`, ...wrongLines].join(
            '\n'
          )
        : '客观题全部答对，无错题。';

      return [
        '你是一位专业的学习顾问，请根据测验数据写一份**详细学情分析报告**。',
        '',
        '【输出要求】',
        '- 语言：中文，语气鼓励、具体、可执行',
        '- 篇幅：350–600 字，必须写完整并有明确结尾',
        '- 格式：用 **加粗小标题** 分四段（不要数字编号列表）：',
        '  **整体表现** — 得分解读与态度鼓励',
        '  **薄弱知识点** — 按题型/考点归纳错题反映的知识盲区',
        '  **逐题诊断** — 结合下方错题明细，点出 3–5 个典型错因（无需每题都讲）',
        '  **记忆锦囊** — 针对薄弱点给 3–5 条「一句话记忆」或「易混对比」（如 A vs B）；每条不超过 2 行',
        '- 禁止：复习日程/分步骤计划、要求画图/仿真/抓包/买设备/闭卷重测等实操安排',
        '',
        '【测验数据】',
        scoreBlock,
        '',
        wrongSection,
      ].join('\n');
    }

    const maxDetail = 6;
    const sample = wrongItems.slice(0, maxDetail);
    const wrongSection = wrongItems.length
      ? `错题 ${wrongItems.length} 题${typeSummary ? `（${typeSummary}）` : ''}，题号：${sample.map((w) => w.no).join('、')}${wrongItems.length > maxDetail ? ' 等' : ''}`
      : '客观题全对。';

    return [
      '你是一位学习顾问，请根据测验结果写一段**精炼学情分析**。',
      '',
      '【输出要求】',
      '- 150–220 字，一段话或两个短段，必须有句号结尾',
      '- 结构：一句鼓励 → 点出 1–2 个薄弱点 → 给 1–2 条记忆口诀或易混对比 → 一句加油',
      '- 禁止：小标题、编号列表、逐题讲解、复习日程/实操计划、空洞套话',
      '',
      '【测验数据】',
      scoreBlock,
      wrongSection,
    ].join('\n');
  }

  async function testConnection() {
    const text = await chatCompletion(
      [{ role: 'user', content: '请只回复：连接成功' }],
      null,
      { maxTokens: 64, temperature: 0, timeoutMs: 120000 }
    );
    return text.trim();
  }

  async function explainQuestion(q, userAnswer, gradeResult, onChunk) {
    const full = hasOwnKey();
    const prompt = buildQuestionPrompt(q, userAnswer, gradeResult, { full });
    return chatCompletion([{ role: 'user', content: prompt }], onChunk, {
      maxTokens: full ? 4096 : 512,
      temperature: full ? 0.6 : 0.5,
      stream: Boolean(onChunk) && full,
    });
  }

  async function analyzeResult(quiz, answers, stats, onChunk) {
    const full = hasOwnKey();
    const prompt = buildResultAnalysisPrompt(quiz, answers, stats, { full });

    if (full) {
      const text = await chatCompletion([{ role: 'user', content: prompt }], onChunk, {
        maxTokens: 4096,
        temperature: 0.6,
        stream: Boolean(onChunk),
      });
      return String(text || '').trim();
    }

    // 站点代理：非流式、低 token，避免 Worker 截断与 429
    const text = await chatCompletion([{ role: 'user', content: prompt }], null, {
      maxTokens: 512,
      temperature: 0.4,
      stream: false,
    });
    const cleaned = String(text || '').trim();
    if (looksIncomplete(cleaned)) {
      throw new Error(
        '分析结果不完整（站点 AI 配额或限流）。请填写自带 Key 启用完整模式，或 1–2 分钟后再试。'
      );
    }
    if (onChunk && cleaned) onChunk(cleaned);
    return cleaned;
  }

  async function ensureUnlocked(passphrase) {
    if (!hasStoredKey() || isKeyUnlocked()) return;
    await unlockKey(passphrase);
  }

  window.QuizAI = {
    PROVIDERS,
    loadSettings,
    saveSettings,
    unlockKey,
    lockKey,
    clearSavedCredentials,
    isFullyCleared,
    getStoredKeyHint,
    ensureUnlocked,
    isConfigured,
    isProxyAvailable,
    usesProxy,
    hasOwnKey,
    hasStoredKey,
    isKeyUnlocked,
    canUseOwnKey,
    getAiMode,
    getAiSourceLabel,
    getProviderInfo,
    getActiveModel,
    formatAiHtml,
    testConnection,
    explainQuestion,
    analyzeResult,
  };
})();
