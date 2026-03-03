package com.pgcore.webhook

import com.pgcore.webhook.util.HmacSigner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// HMAC 서명 단위 테스트 (고정 벡터: secret=whsec_test, ts=1700000000000, expected=v1=58ca6406...)
class HmacSignerTest {

    companion object {
        private const val SECRET = "whsec_test"
        private const val TIMESTAMP = 1700000000000L
        private const val RAW_BODY_JSON = """{"eventType":"PAYMENT_DONE","paymentKey":"pk_test_123"}"""
        private const val EXPECTED_HEX = "58ca6406a95794609644a973cc42c3680aba860c282fd410aa89099dc10b2944"
        private const val EXPECTED_SIGNATURE = "v1=$EXPECTED_HEX"
    }

    @Test
    fun `고정 테스트 벡터 - 서명이 예상값과 일치한다`() {
        val rawBody = RAW_BODY_JSON.toByteArray(Charsets.UTF_8)
        val signature = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)

        assertEquals(EXPECTED_SIGNATURE, signature,
            "서명 알고리즘이 변경되었거나 메시지 포맷이 잘못되었습니다.")
    }

    @Test
    fun `동일 입력으로 두 번 서명하면 결과가 동일하다 (결정적)`() {
        val rawBody = RAW_BODY_JSON.toByteArray(Charsets.UTF_8)
        val sig1 = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)
        val sig2 = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)

        assertEquals(sig1, sig2, "HMAC 서명은 결정적이어야 합니다.")
    }

    @Test
    fun `timestamp가 다르면 서명이 다르다`() {
        val rawBody = RAW_BODY_JSON.toByteArray(Charsets.UTF_8)
        val sig1 = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)
        val sig2 = HmacSigner.sign(SECRET, TIMESTAMP + 1000L, rawBody)

        assertNotEquals(sig1, sig2, "timestamp가 다르면 서명이 달라야 합니다.")
    }

    @Test
    fun `secret이 다르면 서명이 다르다`() {
        val rawBody = RAW_BODY_JSON.toByteArray(Charsets.UTF_8)
        val sig1 = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)
        val sig2 = HmacSigner.sign("different_secret", TIMESTAMP, rawBody)

        assertNotEquals(sig1, sig2, "secret이 다르면 서명이 달라야 합니다.")
    }

    @Test
    fun `서명은 v1= 접두사로 시작한다`() {
        val rawBody = RAW_BODY_JSON.toByteArray(Charsets.UTF_8)
        val signature = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)

        assertTrue(signature.startsWith("v1="), "서명은 'v1=' 로 시작해야 합니다.")
    }

    @Test
    fun `서명 hex 부분은 64자 (SHA-256 = 32bytes = 64 hex chars)`() {
        val rawBody = RAW_BODY_JSON.toByteArray(Charsets.UTF_8)
        val signature = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)
        val hex = HmacSigner.extractHex(signature)

        assertEquals(64, hex?.length, "HMAC-SHA256 hex digest는 64자여야 합니다.")
    }

    @Test
    fun `extractHex는 v1= 접두사를 제거한 hex를 반환한다`() {
        val rawBody = RAW_BODY_JSON.toByteArray(Charsets.UTF_8)
        val signature = HmacSigner.sign(SECRET, TIMESTAMP, rawBody)
        val hex = HmacSigner.extractHex(signature)

        assertEquals(EXPECTED_HEX, hex)
    }
}
