# 在线测验

轻量化答题网页，HTML5 + Tailwind CSS，支持单选题、多选题、判断题、填空题、问答题。

## 使用

双击 `start.bat`，或在项目目录运行：

```bash
python -m http.server 8080
```

浏览器打开 http://localhost:8080

## 题库

- Profinet 工业以太网测验（25 题）
- OPC 规范（24 题）
- MODBUS 协议及应用（15 题）
- 串口及应用（15 题）
- 网络及工业通信综合考核（135 题）

题库文件位于 `data/` 目录，更新题目后同步修改 `data/manifest.json` 即可。

## 导入综合题库

从 docx 重新生成综合题库 JSON：

```bash
python scripts/parse_comprehensive.py
```

## GitHub Pages

Settings → Pages → Deploy from branch → main → `/ (root)`，访问：

https://q2126221702-ux.github.io/geren/
