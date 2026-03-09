package com.pgcore.core.infra.idempotency

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.springframework.util.StreamUtils
import java.io.ByteArrayInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

class CachedBodyHttpServletRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    // 1. 객체 생성 시점에 원본 스트림을 읽어서 byte 배열로 복사해 둡니다.
    private val cachedBody: ByteArray = StreamUtils.copyToByteArray(request.inputStream)

    fun getCachedBody(): ByteArray = cachedBody

    // 2. getInputStream()이 호출될 때마다 복사해둔 byte 배열로 새로운 스트림을 만들어 반환합니다.
    override fun getInputStream(): ServletInputStream {
        return CachedBodyServletInputStream(cachedBody)
    }

    override fun getReader(): BufferedReader {
        return BufferedReader(InputStreamReader(ByteArrayInputStream(cachedBody), characterEncoding ?: "UTF-8"))
    }

    // 커스텀 ServletInputStream 구현체
    private class CachedBodyServletInputStream(cachedBody: ByteArray) : ServletInputStream() {
        private val inputStream = ByteArrayInputStream(cachedBody)

        override fun isFinished(): Boolean = inputStream.available() == 0
        override fun isReady(): Boolean = true
        override fun setReadListener(readListener: ReadListener?) {
        }
        override fun read(): Int = inputStream.read()
    }
}
