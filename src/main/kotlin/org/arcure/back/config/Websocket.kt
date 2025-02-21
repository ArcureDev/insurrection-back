package org.arcure.back.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.arcure.back.game.GameResponse
import org.arcure.back.game.GameService
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Configuration
@EnableWebSocket
class WebSocketConfig(private val handler: WebSocketHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws").setAllowedOrigins("*")
    }
}

@Component
class WebSocketHandler(private val gameService: GameService) : TextWebSocketHandler() {
    private val sessionsByGameId: MutableMap<Long, MutableList<WebSocketSession>> = ConcurrentHashMap()
    private val objectMapper = ObjectMapper()

    init {
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val gameId = message.payload.toLong()
        sessionsByGameId.getOrPut(gameId) { ArrayList() }.add(session)
        session.sendMessage(TextMessage(objectMapper.writeValueAsString("Coucou, tu es bien dans la liste ;)")))
    }

    fun getGameAndNotify(gameId: Long?): GameResponse {
        val game = gameService.getMyGameOrGame(gameId)
        val sessions = sessionsByGameId[game.id] ?: return game
        val removedSessions = mutableListOf<WebSocketSession>()
        sessions.forEach {
            if (!it.isOpen) {
                removedSessions.add(it)
            } else {
                it.sendMessage(TextMessage(objectMapper.writeValueAsString(game)))
            }
        }
        sessions.removeAll(removedSessions)
        return game
    }
}