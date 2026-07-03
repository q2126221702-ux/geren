# 在线测验

静态网页题库，支持单选、多选（高考数学判分规则）、判断、填空、问答等题型。

## 本地运行

```bat
start.bat
```

浏览器打开 http://localhost:8080

## 题库

题目数据在 `data/` 目录，索引见 `data/manifest.json`。

当前包含：

- 工业通信类测验（Profinet / OPC / MODBUS / 串口 / 综合考核）
- WE Learn 实用综合教程 B1U4–U8 翻译练习

## 测试

需先启动本地服务器，再运行：

```bat
python scripts/test_full.py
```

## GitHub Pages

推送到 `main` 分支后，Actions 会自动部署到 GitHub Pages。
