<div align="center">

# 🤖 kb-agent-lite

**轻量级知识库问答智能体 · 单机部署的开箱即用 RAG 服务**

上传设备文档，即可构建一个懂你业务的专业问答智能体

`Java 17` · `Spring Boot 3.3` · `LangChain4j` · `单 jar 部署` · `零外部中间件`

[![JDK](https://img.shields.io/badge/JDK-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.0.0-orange.svg)](https://docs.langchain4j.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## ✨ 它解决什么问题

市政 / 工业场景中，设备手册、规范标准散落在各处，一线人员查起来费时费力。kb-agent-lite 让你：

> 📄 把文档拖进知识库 → 💬 用自然语言提问 → 🎯 得到**基于文档内容**的专业回答（附引用来源与相似度）

| | |
|---|---|
| **一个 jar 跑全部** | 内嵌 Tomcat + H2 + 向量库 + 中文 Embedding 模型，不需要 MySQL / Redis / Qdrant / MinIO |
| **断网可向量化** | 内置 [bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5)（ONNX 进程内推理），中文检索效果好 |
| **LLM 随便换** | 标准 OpenAI 兼容协议：DeepSeek / 通义 / 智谱 / Kimi / vLLM / Ollama，改三行配置即切换 |
| **3C8G 就够** | 唯一的外部依赖是 LLM 服务本身（云端 API 或内网推理机），本机只跑轻量推理 |
| **数据自己拿** | 全部数据落在一个 `data/` 目录，备份 = 拷目录，迁移 = 换机器 |

## 📸 界面预览

> 内置免构建测试页（正式 UI 可按接口文档自行对接）

<!-- 建议在此处插入三张截图：登录页 / 知识库管理 / 对话页（流式回答+引用来源） -->
<!-- ![登录](docs/images/login.png) ![知识库](docs/images/knowledge.png) ![对话](docs/images/chat.png) -->

## 🚀 快速开始

```bash
git clone https://github.com/<your-org>/kb-agent-lite.git
cd kb-agent-lite
mvn package -DskipTests

# 配置你的模型密钥（任选一种）
export LLM_API_KEY=sk-xxx          # Linux / macOS
set LLM_API_KEY=sk-xxx             # Windows CMD

java -Xms512m -Xmx2g -jar target/kb-agent-lite.jar
```

浏览器打开 <http://localhost:8090>，默认口令 `admin123`：

1. 在「知识库」页上传设备文档（pdf / word / excel / ppt / md / txt / rtf），等待向量化完成
2. 在「对话」页提问，回答基于文档内容并自动标注引用来源

## 🏗️ 架构

```
┌────────────────────────── 一个 fat jar ──────────────────────────┐
│  测试页(static) │ REST + SSE API │ RAG 智能体(LangChain4j 1.0)      │
│  Tika 文档解析   │ 内嵌向量库(文件持久化) │ H2 数据库 │ 本地磁盘文件   │
│  bge-small-zh-v1.5 中文 embedding（ONNX 进程内离线推理, 512维）     │
└──────────────────────────────────────────────────────────────────┘
                    │ 唯一外部依赖（OpenAI 兼容协议）
                    ▼
     公有云 LLM（DeepSeek/通义/智谱） 或 私有化推理服务（vLLM/Ollama）
```

| 数据 | 存储 | 说明 |
|---|---|---|
| 文本块 + 向量 | `data/vectors.json` | 内嵌向量库（内存检索 + 文件持久化），几万块规模毫秒级 |
| 文档元数据 / 分类 | H2 `knowledge_document` / `knowledge_category` | 列表分页、状态、删除联动 |
| 会话 / 消息（多轮记忆） | H2 `agent_session` / `agent_message` | 滑动窗口上下文，重启可恢复 |
| 原始文件 | `data/files/` | 预览、重新向量化 |

**备份 = 拷贝 `data/` 目录；迁移 = 拷目录 + 换机器。**

## 🖥️ 私有化部署

在 3C8G 服务器上实测可行：

```
/opt/kb-agent/
├── jdk17/                  # 解压版 JDK（机器已有 JRE 17+ 则不需要）
├── kb-agent-lite.jar
├── application.yml         # 从 config-template.yml 复制修改
└── data/                   # 首次启动自动生成
```

```bash
jdk17/bin/java -Xms512m -Xmx2g -jar kb-agent-lite.jar &
# 升级 = 换 jar 重启；回滚 = 换回旧 jar
```

> 💡 注意：LLM 不在本机运行（3C8G 跑不动本地大模型），通过配置指向云端 API 或内网推理机。

也支持 Docker：`docker build -t kb-agent-lite . && docker run -p 8090:8090 -v /opt/kb-agent/data:/app/data ...`

## 🔌 模型接入（OpenAI 兼容协议）

将 `config-template.yml` 复制为 jar 同目录的 `application.yml`，改三项即可：

| 服务商 | base-url | model 示例 |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| 通义百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| 智谱 | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-air` |
| Kimi | `https://api.moonshot.cn/v1` | `moonshot-v1-8k` |
| vLLM（私有化） | `http://<IP>:8000/v1` | 部署模型名 |
| Ollama（私有化） | `http://<IP>:11434/v1` | `qwen2.5:14b` |

<details>
<summary><b>环境变量速查</b>（点击展开）</summary>

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_BASE_URL` | `https://api.deepseek.com/v1` | 模型 OpenAI 兼容地址 |
| `LLM_API_KEY` | （空） | 模型密钥，**必配** |
| `LLM_MODEL` | `deepseek-chat` | 模型名 |
| `KB_AUTH_PASSWORD` | `admin123` | 访问口令，**上线必改** |
| `KB_TOKEN_SECRET` | 默认值 | token 签名密钥，**上线必改** |
| `KB_DATA_DIR` | `./data` | 数据目录 |
| `KB_AUTH_ENABLED` | `true` | 关闭后免鉴权（仅限隔离内网） |
| `SERVER_PORT` | `8090` | 服务端口 |

连通性自检：对话报 `Authentication Fails` = 地址对但密钥错；`Connection refused` = 地址或网络不通。
</details>

## 🔧 核心配置调优

| 配置 | 默认 | 说明 |
|---|---|---|
| `knowledge.chunk.size / overlap` | 450 / 80 | 切分块大小需匹配 embedding 模型输入上限（bge-small-zh 为 512 token，中文≈1字1token） |
| `knowledge.rag.min-score` | 0.5 | 引用相似度阈值（0~1），对话页可见每条引用的分数 |
| `knowledge.rag.recall-expand-factor` | 5 | 粗召回扩展倍数（召回 → 阈值过滤 → 文档级去重 → top-N） |
| `agent.security.*` | 见代码 | Prompt 注入防火墙 / 输出脱敏 / 对话限流 |

## 📡 前端对接

内置三个免构建测试页（登录 / 知识库管理 / 对话），仅用于联调验收。正式 UI 请基于 [docs/API接口文档.md](docs/API接口文档.md) 对接，其中包含：

- 全部 REST 接口的请求 / 响应契约
- **SSE 流式协议逐事件说明**（`session / status / thinking / token / tool_call / tool_result / sources / complete / error / heartbeat`）
- 断线恢复机制

任何前端框架（Vue / React / 小程序）均可对接，后端零改动。

## ⚠️ 已知限制

- embedding 模型与向量文件绑定（512 维）：更换模型需删除 `data/vectors.json` 并重新上传文档
- 内置模型输入上限 512 token：长文本场景可换 bge-m3 等模型（自行转 ONNX 或走远程服务）
- H2 为单文件库，适合单机小规模；多实例 / 高并发时切 MySQL 只需改数据源配置
- 文档量到十万块级以上时，可按 `VectorStoreService` 接口扩展 Qdrant 实现
- 口令鉴权为单用户模式，无角色权限体系

## 🧭 技术栈

[Spring Boot 3.3](https://spring.io/projects/spring-boot) · [LangChain4j 1.0](https://docs.langchain4j.dev/) · [Apache Tika](https://tika.apache.org/) · [MyBatis-Plus](https://baomidou.com/) · [H2 Database](https://h2database.com/) · [BAAI bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5)

## 📄 License

[MIT](LICENSE) © kb-agent-lite contributors
