# RikkaHub Desktop（Compose Desktop 原生 UI）

RikkaHub 桌面原生客户端，基于 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)（Android 版）重构，以 **Kotlin + Compose Multiplatform Desktop** 实现的 Windows / macOS / Linux 桌面版。

## 简介

RikkaHub Desktop 将 RikkaHub 的核心体验带到桌面：多协议 LLM 对话（OpenAI 兼容 / Claude / Gemini 原生协议）、多助手与长期记忆、联网搜索、工具调用、上下文压缩等，复用 Android 版的数据模型概念与界面设计语言，功能逐步对齐 Android 版。

- **原生渲染**：Skiko 原生绘制，无浏览器内核、不依赖 web-ui，启动快、内存占用低
- **数据本地**：设置 / 会话 / 记忆全部以 JSON 存储在本地数据目录，支持 zip 一键备份与恢复
- **多平台**：一套代码构建 Windows（msi / 绿色版 exe）、macOS（dmg）、Linux（deb）安装包

## 已实现

- ✅ 多协议：OpenAI 兼容 / Claude 原生（thinking + prompt 图片）/ Gemini 原生（thinkingConfig）
- ✅ 预置 21 个 Provider（含 Gemini、MiniMax，与安卓对齐）
- ✅ 助手正则变换（input/output 作用域）+ 自定义请求头/请求体
- ✅ 会话置顶、导出对话为 Markdown
- ✅ 上下文压缩：一键将历史压缩为摘要（可独立指定压缩模型与提示词）
- ✅ TTS 朗读：OpenAI 兼容 /audio/speech，消息旁播放/停止按钮
- ✅ 本地备份/恢复：设置+会话打包 zip 导出/导入（恢复前校验）
- ✅ 自定义主题：主色/背景色 HSL 编辑器（实时预览，明暗双配色）
- ✅ 提示词变量（{{cur_date}}/{{model_id}}/{{nickname}}/{{char}} 等 12 个）+ 助手预置消息（开场白）+ 消息模板
- ✅ 会话操作：Fork 分叉、手动改标题、移动到其他助手
- ✅ 会话内搜索跳转（命中计数/循环跳转/高亮）、消息跳转器（顶/底/上下条）、自由圈选复制
- ✅ 记忆 Memory：三协议工具调用闭环（OpenAI tool_calls / Claude tool_use / Gemini functionCall），模型自主读写长期记忆，支持全局共享 + 管理页
- ✅ 原生窗口（1280×800，最小 960×600）+ 应用图标（窗口/exe/安装包）+ 单实例锁
- ✅ 会话侧栏：新建/搜索/删除会话、按日期分组（今天/昨天/x月x日），JSON 文件持久化
- ✅ 用户信息：昵称/头像编辑（头像自动缩放 128px）+ 时段问候语，持久化
- ✅ 聊天主界面：消息气泡、Markdown 渲染、流式输出实时渲染、自动滚动、停止生成
- ✅ 消息分支：重新生成/编辑会产生新分支变体，可 ◀ 1/2 ▶ 切换（与 Android 版 MessageNode 对齐）
- ✅ 思考链（reasoning）解析与折叠展示（含思考耗时）、token 用量/速度/生成耗时显示
- ✅ 消息操作：复制/编辑/重新生成/删除/翻译/收藏（收藏夹跨会话浏览）
- ✅ 图片附件：文件选择 → base64 多模态消息（OpenAI vision）
- ✅ 文件上传：文本类文档（代码/md/json 等 40+ 扩展名）作为提示词附件
- ✅ 快捷消息：设置页管理，输入栏一键插入
- ✅ 对话建议：生成完成后推荐 4 条快捷回复（对齐 Android generateSuggestion）
- ✅ AI 标题：首轮对话后自动生成会话标题（对齐 Android generateTitle）
- ✅ 联网搜索：Tavily / Exa，结果注入上下文（对齐安卓 search 模块子集）
- ✅ 多助手：增删改、system prompt、绑定模型、完整模型参数（temperature/topP/maxTokens/上下文条数）、推理力度六档
- ✅ 预置 4 个内置助手（通用/翻译/代码/写作）
- ✅ 默认模型分配：标题/建议/翻译/压缩可独立指定模型（对齐安卓 SettingModelPage 子集）
- ✅ 显示设置：字号倍率、消息时间戳/模型头像/模型名/token 用量/思考链开关
- ✅ 主题：浅色/深色/跟随系统 + 6 套预设配色 + 自定义 HSL 配色
- ✅ 输入栏：卡片式，附件菜单 / 快捷消息 / 模型选择 / 推理力度下拉
- ✅ 跨平台数据目录（%APPDATA% / Application Support / XDG）

## 路线图（与 Android 版的差距）

- ⏳ Claude prompt caching、OpenAI Responses API
- ⏳ 提示词注入（mode/lorebook）
- ⏳ LaTeX、代码块行号/折行、字体
- ⏳ 更多搜索服务（Brave/SearXNG/智谱等 16 种）、搜索深度选项
- ⏳ MCP、ASR、Workspace 沙箱、备份同步（WebDAV/S3）、Token 统计、图片生成
- ⏳ 系统托盘、快捷键

## 运行

```bash
# 开发模式运行
./gradlew run

# 打 fatJar
./gradlew shadowJar
java -jar build/libs/rikkahub-desktop-0.6.0-all.jar
```

> 本仓库即独立 Gradle 项目（根目录 `settings.gradle.kts`，rootProject = rikkahub-desktop），
> 仓库源配置含阿里云镜像（应对本机 Maven Central SSL 污染）。

## 数据目录

| 平台 | 路径 |
|---|---|
| Windows | `%APPDATA%/rikkahub` |
| macOS | `~/Library/Application Support/rikkahub` |
| Linux | `~/.local/share/rikkahub` |

## 结构

```
src/main/kotlin/me/rerere/rikkahub/desktop/
├── Main.kt                 # 窗口入口（单实例锁、应用图标）
├── data/
│   ├── Models.kt           # Provider/助手/会话/消息节点/显示设置/快捷消息/记忆 数据模型
│   ├── Presets.kt          # 预置 21 个 Provider、4 个内置助手、推理六档
│   ├── Backup.kt           # 本地备份/恢复（设置+会话 zip 导出导入）
│   ├── MemoryStore.kt      # 长期记忆存储（按助手/全局，JSON）
│   └── Stores.kt           # 跨平台存储（JSON 持久化 + 预置合并）
├── llm/
│   ├── LlmClient.kt        # 多协议统一接口 + 工具调用框架（ToolDefinition/ToolExchange）
│   ├── OpenAiClient.kt     # OpenAI 兼容 SSE 流式客户端（正文/思考链/用量/多模态/tool_calls）
│   ├── ClaudeClient.kt     # Claude 原生协议（thinking_delta / 图片 / budget_tokens / tool_use）
│   ├── GeminiClient.kt     # Gemini 原生协议（thought parts / thinkingConfig / functionCall）
│   ├── PromptVariables.kt  # 提示词变量 + 消息模板渲染
│   ├── TtsClient.kt        # TTS 朗读（OpenAI 兼容 /audio/speech，wav）
│   └── SearchClient.kt     # 联网搜索（Tavily / Exa）
└── ui/
    ├── theme/              # Material3 主题 + 6 套预设配色 + 自定义 HSL 配色
    ├── chat/               # ChatScreen + ChatViewModel（分支/流式/思考链/翻译/收藏/压缩/TTS/记忆/搜索跳转）
    └── settings/           # 设置：Provider、助手、快捷消息、默认模型、联网搜索、TTS、外观、界面、备份恢复、通用
```
