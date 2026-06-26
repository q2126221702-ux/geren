@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 正在启动本地服务器...
echo 请在浏览器打开: http://localhost:8080
echo 按 Ctrl+C 停止
python -m http.server 8080
