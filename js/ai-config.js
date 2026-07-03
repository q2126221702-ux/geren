/**
 * 站点默认 AI：Cloudflare Worker + Gemini（Key 在 Worker Secrets，不暴露给浏览器）。
 */
window.QuizAIConfig = {
  proxyUrl: 'https://quiz-ai-proxy.favoritism.workers.dev/v1',
  proxyModel: 'gemini-2.0-flash-lite',
};
