/**
 * 站点默认 AI：Cloudflare Worker + 智谱 GLM-4-Flash（Key 在 Worker Secrets，不暴露给浏览器）。
 */
window.QuizAIConfig = {
  // 仅使用自定义域名（workers.dev 在国内移动网络常不可用）
  proxyUrl: 'https://ai.488227.xyz/v1',
  proxyModel: 'glm-4-flash',
};
