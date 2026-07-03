/**
 * 本地覆盖配置（可选，已 gitignore）。
 * 复制本文件为 js/ai-config.local.js 后按需修改。
 *
 * 方案 A — 关闭站点代理，只用浏览器里填的自带 Key（推荐本地调试）：
 */
// window.QuizAIConfig = Object.assign(window.QuizAIConfig || {}, {
//   proxyUrl: '',
// });

/**
 * 方案 B — 本机 ChatAnywhere 代理（需 start-ai.bat + .env，不能走 Cloudflare）：
 */
// window.QuizAIConfig = Object.assign(window.QuizAIConfig || {}, {
//   proxyUrl: 'http://localhost:8787/v1',
//   proxyModel: 'gpt-4o-mini',
// });
