/**
 * 站点默认 AI：Cloudflare Worker + 智谱 GLM-4-Flash（Key 在 Worker Secrets，不暴露给浏览器）。
 */
window.QuizAIConfig = {
  proxyUrls: [
    'https://ai.488227.xyz/v1',
    'https://quiz-ai-proxy.favoritism.workers.dev/v1',
  ],
  proxyModel: 'glm-4-flash',
};
