# RikkaHub Desktop（Compose Desktop 原生 UI）

桌面原生客户端：**Kotlin + Compose Multiplatform Desktop**，Skiko 原生渲染，无浏览器内核、不依赖 web-ui。

UI 方案借鉴自 `rikkahub-desktop-native` 参考项目，并逐步对齐 Android 版功能。

## 已实现

- ✅ 原生窗口（1280×800，最小 960×600）
- ✅ 会话侧栏：新建/搜索/删除会话、按日期分组（今天/昨天/x月x日），JSON 文件持久化
- ✅ 聊天主界面：消息气泡、Markdown 渲染、流式输出实时渲染、自动滚动、停止生成
- ✅ 消息分支：重新生成/编辑会产生新分支变体，可 ◀ 1/2 ▶ 切换（与 Android 版 MessageNode 对齐）
- ✅ 思考链（reasoning）解析与折叠展示（含思考耗时）、token 用量/速度/生成耗时显示
- ✅ 图片附件：文件选择 → base64 多模态消息（OpenAI vision）
- ✅ 多助手：增删改、system prompt、绑定模型、推理力度（low/medium/high），会话级助手绑定
- ✅ 消息区：模型头像 + 名称 + 时间戳、常驻操作栏（复制/编辑/重新生成/删除）
- ✅ 输入栏：卡片式，模型选择 / 推理力度下拉
- ✅ Provider 设置：增删改查、启用切换、模型选择、一键拉取模型列表、系统提示词、温度滑条
- ✅ 主题：浅色/深色/跟随系统 + 6 套预设配色（暖橙/樱花/海洋/春日/秋日/暗黑，移植自 Android 版）
- ✅ OpenAI 兼容协议流式客户端（DeepSeek/硅基流动/Kimi/OpenAI/聚合网关）
- ✅ 跨平台数据目录（%APPDATA% / Application Support / XDG）

## 路线图（与 Android 版的差距）

- ⏳ 自定义主题颜色（primary/secondary/tertiary + 导入导出）
- ⏳ 附件（图片/文档）、联网搜索、推理强度设置
- ⏳ 助手进阶：topP/context size/maxTokens、自定义请求头/体、正则变换、提示词注入、记忆
- ⏳ 接入上游 `:ai` 模块（Claude/Gemini 原生协议、MCP、工具调用）
- ⏳ 显示设置：字号倍率、字体、代码块行号/折行、LaTeX、聊天背景
- ⏳ TTS/ASR、Workspace 沙箱、备份同步、Token 统计、翻译器、图片生成
- ⏳ 系统托盘、快捷键、原生安装包（`./gradlew packageDistributionForCurrentOS`）

## 运行

```bash
# 开发模式运行（在本目录或仓库根目录均可）
../gradlew run

# 打 fatJar
../gradlew shadowJar
java -jar build/libs/rikkahub-desktop-0.3.0-all.jar
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
