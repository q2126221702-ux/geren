@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo [1/2] 启动 ChatAnywhere 本地代理（Key 从 .env 读取）...
echo       站点默认 AI 用 Cloudflare+Gemini 时无需本步骤。
start "quiz-ai-proxy" cmd /c "python scripts\ai_proxy.py"
timeout /t 1 /nobreak >nul
echo [2/2] 启动测验站点...
echo.
echo 浏览器打开: http://localhost:8080
echo 若已配置 ai-config.local.js 指向本机代理: http://127.0.0.1:8787/v1
echo 按 Ctrl+C 可停止网页服务（代理窗口请单独关闭）
echo.
python -m http.server 8080
