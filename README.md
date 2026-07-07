# 在线测验

静态网页题库，支持单选、多选（高考数学判分规则）、判断、填空、问答等题型。数据在 `data/`，索引见 `data/manifest.json`。

## 本地运行

```bat
start.bat
```

浏览器打开 http://localhost:8080

## 功能概览

| 模块 | 说明 |
|------|------|
| 首页分类 | **工业以太网** / **英语** 两类入口，记住上次选择 |
| 工业题库 | Profinet、OPC、MODBUS、串口、综合考核 |
| 期末卷 | 工业网络技术期末考核（100 分）· 5 套 A~E + **随机抽卷**选题页 |
| 英语题库 | B1U4~U8 翻译、语法选择、iWords 填空、单词速记闪卡 |
| 错题集 | 交卷自动收录客观错题，本机 `localStorage` 持久化；筛选、练习、AI 错因、导出、单题/批量移除 |
| 错词突击 | iWords 交卷后「换形式再练错词」（仅当次会话） |
| 待复习 | 解析模式下浏览错题与问答题（问答题对照参考，不进错题集） |
| AI | 逐题解析、交卷学情分析；站点默认 Worker 或自带 Key |

## 题库

当前 manifest 共 **18** 项：

- **工业（6）**：Profinet / OPC / MODBUS / 串口 / 综合考核 / 期末 100 分（exam_pack，variants A~E）
- **英语（12）**：5 单元翻译、语法 50 题、5 单元 iWords、B1U4~U8 单词速记

从 `welearn-output/` 重新生成 WE Learn 题库：

```bat
python scripts/import_welearn.py
python scripts/import_iwords.py
```

期末卷生成与审查：

```bat
python scripts/build_industrial_exam_100.py
python scripts/deep_check_exams.py
python scripts/audit_exam_papers.py
```

## 前端脚本结构

| 文件 | 职责 |
|------|------|
| `js/quiz-meta.js` | 工业/英语分类等共享常量 |
| `js/wrong-book.js` | 错题集存储、筛选、导出、组卷 |
| `js/ai-config.js` | 站点 AI Worker 地址（无 Key） |
| `js/ai.js` | AI 解析、学情分析、BYOK |
| `js/app.js` | 答题流程、UI、判分 |

## AI 功能

| 功能 | 说明 |
|------|------|
| AI 解析 | 复习模式下逐题讲解 |
| AI 学情分析 | 交卷后根据得分与错题生成建议 |
| 错题集 AI | 错题卡片上「AI 错因分析」，结果缓存在本机 |
| AI 设置 | 用户自带 API Key（BYOK），保存在浏览器 localStorage |

### 站点默认 AI（免费）

未填写自带 Key 时，使用 **Cloudflare Worker → 智谱 GLM**（国内直连）：

- API Key 存在 Worker Secrets（`ZHIPU_API_KEY`），**不会**出现在前端或 Git
- 前端配置见 `js/ai-config.js`（仅 Worker 地址）
- 共享站点配额，请勿频繁点击；可填写自己的智谱 Key 启用完整模式

### 自带 Key（BYOK）· 完整模式

| 能力 | 完整模式（自带 Key） | 站点默认 AI |
|------|---------------------|-------------|
| 学情分析 | 流式、350–600 字、含全部错题 | 精简、非流式 |
| 重新分析 | 支持 | 仅查看缓存 |
| 点击冷却 | 无 | 90 秒 |
| 测试连接冷却 | 无 | 15 秒 |

支持 Gemini、DeepSeek、Moonshot/Kimi、智谱、通义千问、硅基流动、火山引擎/豆包、GitHub Models、ChatAnywhere 等。

使用 AI 时，题目与作答会发送至对应服务商；Key 与加密口令不会上传至本站。

### 部署 Cloudflare Worker

详见 [`workers/README.md`](workers/README.md)。简要步骤：

1. [智谱开放平台](https://open.bigmodel.cn/usercenter/apikeys) 申请 Key
2. 部署 `workers/ai-proxy.js`，Secrets 添加 `ZHIPU_API_KEY`
3. `js/ai-config.js` 写入 Worker 的 `proxyUrl`
4. `ALLOWED_ORIGINS` 加入 GitHub Pages 域名
5. 建议绑定 KV `AI_RATE` 做 IP 限流

### 本地可选：ChatAnywhere 代理

```bat
copy .env.example .env
copy js\ai-config.local.example.js js\ai-config.local.js
start-ai.bat
```

日常调试可直接 `start.bat`（与线上一致走 Worker）。

### 勿提交到 Git

`.gitignore` 已排除：`.env`、`js/ai-config.local.js`、`scripts/_*.png` 等本地调试产物。

## 测试

先 `start.bat`，再运行（默认 `http://localhost:8080`，可传参覆盖）：

```bat
python scripts/validate_data.py
python scripts/audit_iwords_fill.py
python scripts/deep_check_exams.py
python scripts/test_multichoice_gaokao.py
python scripts/test_full.py
python scripts/test_wrong_book.py
python scripts/test_iwords.py
python scripts/test_welearn_quiz.py
python scripts/test_vocab_flashcard.py
```

`test_full.py` 覆盖 18 套题库加载、判分、移动端关键流程、期末选题页、错题集入口（约 132 项）。

## GitHub Pages

推送到 `main` 后 Actions 自动部署（失败时会等待 45 秒重试一次）。

在线：https://q2126221702-ux.github.io/geren/
