package com.pgcore.webhook.util

import com.pgcore.webhook.application.usecase.repository.WebhookSendClient
import com.pgcore.webhook.application.usecase.repository.dto.WebhookSendResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

// HMAC-SHA256 서명 포함 웹훅 HTTP POST 전송 클라이언트 (secret은 절대 로그 출력 금지)
@Component
class WebhookHttpClient(
    @Value("\${webhook.http.connect-timeout-ms:1000}") private val connectTimeoutMs: Long,
    @Value("\${webhook.http.read-timeout-ms:3000}") private val readTimeoutMs: Long,
) : WebhookSendClient {
    private val log = LoggerFactory.getLogger(javaClass)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        .build()

    companion object {
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        private const val HEADER_TIMESTAMP = "X-PG-Timestamp"
        private const val HEADER_EVENT_ID = "X-PG-Event-Id"
        private const val HEADER_SIGNATURE = "X-PG-Signature"
    }

    override fun send(
        url: String,
        secret: String,
        eventId: Long,
        payloadSnapshot: String,
    ): WebhookSendResult {
        val rawBody = payloadSnapshot.toByteArray(Charsets.UTF_8)
        val timestamp = Instant.now().toEpochMilli()
        val signature = HmacSigner.sign(secret, timestamp, rawBody)

        log.debug("[WebhookSend] eventId={} url={} timestamp={}", eventId, url, timestamp)

        val request = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofByteArray(rawBody))
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .header(HEADER_TIMESTAMP, timestamp.toString())
            .header(HEADER_EVENT_ID, eventId.toString())
            .header(HEADER_SIGNATURE, signature)
            .timeout(Duration.ofMillis(readTimeoutMs))
            .build()

        val start = System.currentTimeMillis()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("HTTP 전송 인터럽트: eventId=$eventId", e)
        }
        val responseMs = System.currentTimeMillis() - start

        log.debug("[WebhookSend] eventId={} status={} responseMs={}", eventId, response.statusCode(), responseMs)
        return WebhookSendResult(httpStatus = response.statusCode(), responseMs = responseMs)
    }
}
