package me.rerere.rikkahub.desktop.data

/**
 * 预置 Provider / 助手（移植自 Android 版 DEFAULT_PROVIDERS 与内置助手）。
 * 桌面端走 OpenAI 兼容协议，因此只预置 OpenAI 兼容的 Provider。
 * 首次启动时合并进用户设置，用户只需填对应 Provider 的 API Key。
 */

private fun provider(id: String, name: String, baseUrl: String, models: List<String>, type: String = "openai") =
    ProviderConfig(id = id, name = name, baseUrl = baseUrl, apiKey = "", models = models, type = type)

val DEFAULT_PROVIDERS: List<ProviderConfig> = listOf(
    provider(
        "a0000000-0000-4000-9000-0000000000a1", "RikkaHub", "https://api.rikka-ai.com/v1",
        listOf("auto")
    ),
    provider(
        "1eeea727-9ee5-4cae-93e6-6fb01a4d051e", "OpenAI", "https://api.openai.com/v1",
        listOf("gpt-4o", "gpt-4o-mini")
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000b1", "Gemini", "https://generativelanguage.googleapis.com",
        listOf("gemini-2.5-flash", "gemini-2.5-pro"), type = "google"
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000b2", "MiniMax", "https://api.minimaxi.com/anthropic/v1",
        emptyList(), type = "claude"
    ),
    provider(
        "f099ad5b-ef03-446d-8e78-7e36787f780b", "DeepSeek", "https://api.deepseek.com/v1",
        listOf("deepseek-chat", "deepseek-reasoner")
    ),
    provider(
        "56a94d29-c88b-41c5-8e09-38a7612d6cf8", "硅基流动", "https://api.siliconflow.cn/v1",
        listOf("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen2.5-7B-Instruct")
    ),
    provider(
        "d5734028-d39b-4d41-9841-fd648d65440e", "OpenRouter", "https://openrouter.ai/api/v1",
        listOf("openai/gpt-4o", "openai/gpt-4o-mini", "anthropic/claude-sonnet-4", "google/gemini-2.5-flash")
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a2", "Vercel AI Gateway", "https://ai-gateway.vercel.sh/v1",
        emptyList()
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a3", "小马算力", "https://api.tokenpony.cn/v1",
        emptyList()
    ),
    provider(
        "d6c4d8c6-3f62-4ca9-a6f3-7ade6b15ecc3", "月之暗面", "https://api.moonshot.cn/v1",
        listOf("kimi-k3", "kimi-k2-0711-preview")
    ),
    provider(
        "3bc40dc1-b11a-46fa-863b-6306971223be", "智谱AI开放平台", "https://open.bigmodel.cn/api/paas/v4",
        listOf("glm-4-plus", "glm-4-flash")
    ),
    provider(
        "f76cae46-069a-4334-ab8e-224e4979e58c", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1",
        listOf("qwen-max", "qwen-plus", "qwen-turbo")
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a4", "火山引擎", "https://ark.cn-beijing.volces.com/api/v3",
        emptyList()
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a5", "阶跃星辰", "https://api.stepfun.com/v1",
        emptyList()
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a6", "腾讯Hunyuan", "https://api.hunyuan.cloud.tencent.com/v1",
        emptyList()
    ),
    provider(
        "ff3cde7e-0f65-43d7-8fb2-6475c99f5990", "xAI", "https://api.x.ai/v1",
        listOf("grok-3", "grok-3-mini")
    ),
    provider(
        "1b1395ed-b702-4aeb-8bc1-b681c4456953", "AiHubMix", "https://aihubmix.com/v1",
        listOf("gpt-4o", "claude-sonnet-4-20250514", "gemini-2.5-flash")
    ),
    provider(
        "da93779f-3956-48cc-82ef-67bb482eaaf7", "302.AI", "https://api.302.ai/v1",
        listOf("gpt-4o", "gpt-4o-mini")
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a7", "随想AI网关", "https://sui-xiang.com/v1",
        emptyList()
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a8", "MIMO", "https://api.xiaomimimo.com/v1",
        emptyList()
    ),
    provider(
        "a0000000-0000-4000-9000-0000000000a9", "AckAI", "https://ackai.fun/v1",
        emptyList()
    ),
)

val BUILT_IN_ASSISTANTS: List<Assistant> = listOf(
    Assistant(
        id = "5a1a0001-0001-4a01-9001-000000000001",
        name = "通用助手"
    ),
    Assistant(
        id = "5a1a0001-0001-4a01-9001-000000000002",
        name = "翻译助手",
        systemPrompt = "你是一名专业翻译。请把用户输入的内容翻译成目标语言：中文内容翻译成英文，其他语言翻译成中文。只输出译文，不要解释。保留原文格式。"
    ),
    Assistant(
        id = "5a1a0001-0001-4a01-9001-000000000003",
        name = "代码助手",
        systemPrompt = "你是一名资深软件工程师。回答以代码为核心，给出可直接运行的示例，并简要说明关键点。默认使用用户提问所用的语言。"
    ),
    Assistant(
        id = "5a1a0001-0001-4a01-9001-000000000004",
        name = "写作助手",
        systemPrompt = "你是一名中文写作助手。帮助用户起草、润色、扩写各类文本，注意结构清晰、语言自然。除非用户要求，否则使用中文回复。"
    ),
)

/** 推理力度档位（与 Android 版 ReasoningLevel 对齐：effort 直传 OpenAI 兼容 API） */
val REASONING_EFFORTS: List<Pair<String?, String>> = listOf(
    null to "自动",
    "none" to "关闭",
    "low" to "低",
    "medium" to "中等",
    "high" to "高",
    "xhigh" to "超高",
)
