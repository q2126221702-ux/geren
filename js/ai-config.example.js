/**
 * 复制为 ai-config.js 并填写代理地址（不要在此文件写 API Key）。
 *
 * 线上：部署 workers/ai-proxy.js 到 Cloudflare Workers 后，填入 workers.dev 地址。
 * 本地：默认与线上一致；若用本机 ChatAnywhere 代理，见 ai-config.local.example.js。
 */
window.QuizAIConfig = {
  // 示例：'https://quiz-ai-proxy.你的子域.workers.dev/v1'
  proxyUrl: '',

  // 站点默认模型（智谱 GLM-4.7-Flash 免费，经 Worker 转发）
  proxyModel: 'glm-4.7-flash',
};
