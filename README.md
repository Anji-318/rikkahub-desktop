# RikkaHub Desktop（Compose Desktop 原生 UI）

桌面原生客户端：**Kotlin + Compose Multiplatform Desktop**，Skiko 原生渲染，无浏览器内核、不依赖 web-ui。

UI 方案借鉴自 `rikkahub-desktop-native` 参考项目，并逐步对齐 Android 版功能。

## 已实现

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
- ✅ 预置 19 个 OpenAI 兼容 Provider（与安卓 21 个对齐，Gemini 原生协议与 Claude 协议的 MiniMax 除外）
- ✅ 预置 4 个内置助手（通用/翻译/代码/写作）
- ✅ 默认模型分配：标题/建议/翻译可独立指定模型（对齐安卓 SettingModelPage 子集）
- ✅ 显示设置：字号倍率、消息时间戳/模型头像/模型名/token 用量/思考链开关
- ✅ 主题：浅色/深色/跟随系统 + 6 套预设配色（暖橙/樱花/海洋/春日/秋日/暗黑，移植自 Android 版）
- ✅ 输入栏：卡片式，附件菜单 / 快捷消息 / 模型选择 / 推理力度下拉
- ✅ 跨平台数据目录（%APPDATA% / Application Support / XDG）

## 路线图（与 Android 版的差距）

- ⏳ Claude/Gemini 原生协议（含 prompt caching、Responses API）
- ⏳ 助手进阶：自定义请求头/体、正则变换、提示词注入（mode/lorebook）、记忆
- ⏳ 自定义主题颜色（HSL 编辑器 + 导入导出）、LaTeX、代码块行号/折行、字体
- ⏳ 更多搜索服务（Brave/SearXNG/智谱等 16 种）、搜索深度选项
- ⏳ MCP、TTS/ASR、Workspace 沙箱、备份同步（WebDAV/S3）、Token 统计、图片生成
- ⏳ 系统托盘、快捷键

## 运行

```bash
# 开发模式运行
./gradlew run

# 打 fatJar
./gradlew shadowJar
java -jar build/libs/rikkahub-desktop-0.4.0-all.jar
```

> 本目录自带 `settings.gradle.kts`，既可作为独立 Gradle 项目构建，
> 也可从仓库根目录以 `:desktop` 子项目构建（注意根项目启用
> `FAIL_ON_PROJECT_REPOS`，因此本模块不得声明自己的 `repositories` 块；
> 仓库源配置在 `settings.gradle.kts` 中，含阿里云镜像以应对本机
> Maven Central SSL 污染问题）。

## 数据目录

| 平台 | 路径 |
|---|---|
| Windows | `%APPDATA%/rikkahub` |
| macOS | `~/Library/Application Support/rikkahub` |
| Linux | `~/.local/share/rikkahub` |

## 结构

```
src/main/kotlin/me/rerere/rikkahub/desktop/
├── Main.kt                 # 窗口入口
├── data/Models.kt          # Provider/助手/会话/消息节点数据模型
├── data/Stores.kt          # 跨平台存储（JSON 持久化）
├── llm/OpenAiClient.kt     # OpenAI 兼容 SSE 流式客户端（正文/思考链/用量）
└── ui/
    ├── theme/              # Material3 主题 + 6 套预设配色（移植自 Android 版）
    ├── chat/               # ChatScreen + ChatViewModel（分支/流式/思考链）
    └── settings/           # 设置：Provider、助手、外观、通用
```
