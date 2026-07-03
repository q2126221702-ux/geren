#!/usr/bin/env python3
"""本地 ChatAnywhere 代理：Key 只读 .env，浏览器只连 localhost。

站点默认 AI 已改为 Cloudflare Worker + Gemini；本脚本为可选方案。
ChatAnywhere 禁止经 Cloudflare 等反向代理访问，仅能本机直连使用。
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = "https://api.chatanywhere.tech/v1/chat/completions"
PORT = int(os.environ.get("AI_PROXY_PORT", "8787"))

ALLOWED_ORIGINS = {
    "http://localhost:8080",
    "http://127.0.0.1:8080",
    None,
}


def load_dotenv():
    env_path = ROOT / ".env"
    if not env_path.exists():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))


load_dotenv()
API_KEY = os.environ.get("CHATANYWHERE_API_KEY", "").strip()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[ai-proxy] {self.address_string()} {fmt % args}")

    def _cors(self):
        origin = self.headers.get("Origin")
        if origin in ALLOWED_ORIGINS or origin is None:
            if origin:
                self.send_header("Access-Control-Allow-Origin", origin)
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Max-Age", "86400")

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        if not API_KEY:
            self._json(500, {"error": {"message": "未配置 CHATANYWHERE_API_KEY，请创建 .env"}})
            return

        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        try:
            json.loads(body)
        except json.JSONDecodeError:
            self._json(400, {"error": {"message": "Invalid JSON"}})
            return

        req = Request(
            UPSTREAM,
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {API_KEY}",
            },
        )
        try:
            with urlopen(req, timeout=120) as resp:
                data = resp.read()
                self.send_response(resp.status)
                self._cors()
                ct = resp.headers.get("Content-Type", "application/json")
                self.send_header("Content-Type", ct)
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
        except HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            self.send_response(e.code)
            self._cors()
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(err_body.encode("utf-8"))

    def _json(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self._cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    if not API_KEY:
        print("警告: 未找到 CHATANYWHERE_API_KEY")
        print(f"请复制 {ROOT / '.env.example'} 为 {ROOT / '.env'} 并填入 Key")
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"AI 代理已启动: http://127.0.0.1:{PORT}/v1/chat/completions")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
        return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
