package ru.otus.highload.gateway.ws

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


@Component
class FeedPostedWebSocketHandler(
    meterRegistry: MeterRegistry,
    @Value("\${app.ws.send-timeout-ms:10000}") private val sendTimeoutMs: Int = 10_000,
    @Value("\${app.ws.buffer-limit-bytes:524288}") private val bufferLimitBytes: Int = 512 * 1024,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(FeedPostedWebSocketHandler::class.java)
    private val sessions = ConcurrentHashMap<UUID, CopyOnWriteArrayList<WebSocketSession>>()
    private val guardedByOriginal = ConcurrentHashMap<WebSocketSession, WebSocketSession>()

    private val connectionsGauge = Gauge
        .builder("ws.connections") { connectionCount().toDouble() }
        .register(meterRegistry)
    private val pushed = Counter.builder("ws.events.pushed").register(meterRegistry)
    private val pushFailures = Counter.builder("ws.events.push_failures").register(meterRegistry)
    private val pushTimer = Timer.builder("ws.push.duration").publishPercentileHistogram().register(meterRegistry)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes[AuthTokenHandshakeInterceptor.USER_ID_ATTR] as? UUID
            ?: throw IllegalStateException("WS session without authenticated userId")
        val guarded = ConcurrentWebSocketSessionDecorator(session, sendTimeoutMs, bufferLimitBytes)
        guardedByOriginal[session] = guarded
        sessions.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(guarded)
        log.debug("WS connected: user={}, sessionId={}", userId, session.id)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes[AuthTokenHandshakeInterceptor.USER_ID_ATTR] as? UUID ?: return
        val guarded = guardedByOriginal.remove(session) ?: session
        sessions.computeIfPresent(userId) { _, userSessions ->
            userSessions.remove(guarded)
            if (userSessions.isEmpty()) null else userSessions
        }
        log.debug("WS closed: user={}, sessionId={}, status={}", userId, session.id, status)
    }

    /** Спецификация определяет только server→client; клиентские текстовые кадры игнорируем. */
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) = Unit

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.debug("WS transport error: sessionId={}, error={}", session.id, exception.toString())
        closeQuietly(session)
    }

    /**
     * Доставка события во все открытые локальные сессии пользователя.
     * Возвращает число таких сессий, чтобы listener мог отметить отсутствие адресата.
     */
    fun pushToUser(userId: UUID, json: String): Int {
        val openSessions = sessions[userId]?.filter { it.isOpen }.orEmpty()
        openSessions.forEach { session ->
            pushTimer.record(Runnable {
                try {
                    if (session.isOpen) session.sendMessage(TextMessage(json))
                    pushed.increment()
                } catch (e: Exception) {
                    pushFailures.increment()
                    log.debug("WS push failed: user={}, sessionId={}, error={}", userId, session.id, e.toString())
                    closeQuietly(session)
                }
            })
        }
        return openSessions.size
    }

    /** Keep-alive: прокси/LB рвут тихие соединения (nginx proxy_read_timeout). */
    @Scheduled(fixedRateString = "\${app.ws.ping-interval-ms:30000}")
    fun pingAll() {
        val ping = PingMessage(ByteBuffer.allocate(0))
        sessions.values.forEach { userSessions ->
            userSessions.forEach { session ->
                try {
                    if (session.isOpen) session.sendMessage(ping)
                } catch (_: Exception) { }
            }
        }
    }

    fun connectionCount(): Int = sessions.values.sumOf { it.size }

    private fun closeQuietly(session: WebSocketSession) {
        try {
            session.close(CloseStatus.SERVER_ERROR)
        } catch (e: Exception) {
            log.debug("WS close failed: sessionId={}, error={}", session.id, e.toString())
        }
    }
}
