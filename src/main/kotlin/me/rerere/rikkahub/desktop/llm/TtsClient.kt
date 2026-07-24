package me.rerere.rikkahub.desktop.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.desktop.data.ProviderConfig

/**
 * OpenAI 兼容 TTS 客户端（POST /audio/speech），返回 wav 字节。
 */
class TtsClient {
    private val client = HttpClient(CIO) {
        engine { requestTimeout = 120_000 }
    }

    /** 合成语音，失败返回 null */
    suspend fun speak(provider: ProviderConfig, model: String, voice: String, text: String): ByteArray? = runCatching {
        val url = provider.baseUrl.trimEnd('/') + "/audio/speech"
        val body = buildJsonObject {
            put("model", model)
            put("voice", voice)
            put("input", text)
            put("response_format", "wav")
        }
        val resp = client.post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${provider.apiKey}")
            setBody(body.toString())
        }
        if (resp.status.value >= 400) return null
        resp.bodyAsBytes()
    }.getOrNull()
}
