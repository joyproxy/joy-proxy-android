package com.joyproxy.app.network

import com.joyproxy.app.config.ProxyProtocol
import com.joyproxy.app.config.ProxySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.min

object ProxyTester {
    data class Result(
        val success: Boolean,
        val message: String,
        val latencyMs: Long = 0,
    )

    private const val TEST_HOST = "www.baidu.com"
    private const val TEST_PORT = 443
    private const val TIMEOUT_MS = 8000

    suspend fun test(settings: ProxySettings): Result = withContext(Dispatchers.IO) {
        if (!settings.isValid()) {
            return@withContext Result(false, "请先填写有效的代理地址和端口")
        }

        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(settings.host, settings.port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                when (settings.protocol) {
                    ProxyProtocol.SOCKS5 -> testSocks5(settings, input, output)
                    ProxyProtocol.HTTP -> testHttp(settings, input, output)
                }
            }
            val latency = System.currentTimeMillis() - start
            Result(true, "代理连通正常，延迟 ${latency}ms", latency)
        } catch (e: Exception) {
            Result(false, "连接失败：${friendlyMessage(e)}")
        }
    }

    private fun testSocks5(settings: ProxySettings, input: InputStream, output: OutputStream) {
        val hasAuth = settings.username.isNotBlank()
        if (hasAuth) {
            output.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            output.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        output.flush()

        val methodResponse = readExact(input, 2)
        if (methodResponse[0] != 0x05.toByte()) error("SOCKS5 握手失败")
        if (methodResponse[1] == 0xFF.toByte()) error("代理不支持当前认证方式")

        if (methodResponse[1] == 0x02.toByte()) {
            val user = settings.username.toByteArray(StandardCharsets.UTF_8)
            val pass = settings.password.toByteArray(StandardCharsets.UTF_8)
            val auth = ByteArray(3 + user.size + pass.size)
            auth[0] = 0x01
            auth[1] = user.size.toByte()
            System.arraycopy(user, 0, auth, 2, user.size)
            auth[2 + user.size] = pass.size.toByte()
            System.arraycopy(pass, 0, auth, 3 + user.size, pass.size)
            output.write(auth)
            output.flush()

            val authResponse = readExact(input, 2)
            if (authResponse[1] != 0x00.toByte()) error("代理认证失败，请检查用户名和密码")
        }

        val hostBytes = TEST_HOST.toByteArray(StandardCharsets.UTF_8)
        val request = ByteArray(7 + hostBytes.size)
        request[0] = 0x05
        request[1] = 0x01
        request[2] = 0x00
        request[3] = 0x03
        request[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, request, 5, hostBytes.size)
        request[5 + hostBytes.size] = (TEST_PORT shr 8).toByte()
        request[6 + hostBytes.size] = (TEST_PORT and 0xFF).toByte()
        output.write(request)
        output.flush()

        val connectResponse = readExact(input, 4)
        if (connectResponse[1] != 0x00.toByte()) {
            error("代理无法转发流量：${socks5Error(connectResponse[1].toInt() and 0xFF)}")
        }
        skipSocks5Address(input, connectResponse[3])
    }

    private fun testHttp(settings: ProxySettings, input: InputStream, output: OutputStream) {
        val target = "$TEST_HOST:$TEST_PORT"
        val builder = StringBuilder()
            .append("CONNECT ").append(target).append(" HTTP/1.1\r\n")
            .append("Host: ").append(target).append("\r\n")
        if (settings.username.isNotBlank()) {
            val token =
                Base64.getEncoder().encodeToString(
                    "${settings.username}:${settings.password}".toByteArray(StandardCharsets.UTF_8),
                )
            builder.append("Proxy-Authorization: Basic ").append(token).append("\r\n")
        }
        builder.append("Connection: close\r\n\r\n")
        output.write(builder.toString().toByteArray(StandardCharsets.UTF_8))
        output.flush()

        val response = readLine(input)
        if (!response.startsWith("HTTP/1.")) error("HTTP 代理响应异常")
        if (!response.contains(" 200 ")) error("HTTP 代理拒绝连接：$response")
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read < 0) error("代理连接已断开")
            offset += read
        }
        return buffer
    }

    private fun readLine(input: InputStream): String {
        val buffer = StringBuilder()
        while (true) {
            val ch = input.read()
            if (ch < 0) break
            if (ch == '\n'.code) break
            if (ch != '\r'.code) buffer.append(ch.toChar())
        }
        return buffer.toString()
    }

    private fun skipSocks5Address(input: InputStream, addressType: Byte) {
        when (addressType) {
            0x01.toByte() -> readExact(input, 4 + 2)
            0x03.toByte() -> {
                val length = input.read()
                readExact(input, length + 2)
            }
            0x04.toByte() -> readExact(input, 16 + 2)
            else -> error("SOCKS5 地址格式异常")
        }
    }

    private fun socks5Error(code: Int): String =
        when (code) {
            1 -> "一般性失败"
            2 -> "规则不允许连接"
            3 -> "网络不可达"
            4 -> "主机不可达"
            5 -> "连接被拒绝"
            6 -> "TTL 超时"
            7 -> "不支持的命令"
            8 -> "不支持的地址类型"
            else -> "错误码 $code"
        }

    private fun friendlyMessage(error: Exception): String {
        val message = error.message ?: error.javaClass.simpleName
        return when {
            message.contains("ECONNREFUSED", ignoreCase = true) -> "连接被拒绝，请检查 IP 和端口"
            message.contains("timed out", ignoreCase = true) -> "连接超时，请检查网络或代理是否在线"
            message.contains("Unable to resolve host", ignoreCase = true) -> "无法解析代理地址"
            message.contains("Network is unreachable", ignoreCase = true) -> "网络不可达"
            else -> message.take(min(message.length, 120))
        }
    }
}
