package org.arcure.back.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.arcure.back.game.GameEntity
import org.arcure.back.game.GameMapper
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread


@Component
class SSEComponent(private val gameMapper: GameMapper) {
    private val sses: MutableMap<Long, MutableList<SseEmitter>> = ConcurrentHashMap()

    private val objectMapper = ObjectMapper()

    init {
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun addSse(gameId: Long): SseEmitter {
        val sseEmitters = sses.getOrPut(gameId) { mutableListOf() }
        val sseEmitter = SseEmitter(Int.MAX_VALUE.toLong())
        sseEmitters.add(sseEmitter)
        sseEmitter.onCompletion { sseEmitters.remove(sseEmitter) }
        sseEmitter.onTimeout { sseEmitters.remove(sseEmitter) }
        sseEmitter.onError { _ -> sseEmitters.remove(sseEmitter) }
        return sseEmitter
    }

    fun removeSSE(userId: Long) {
        sses.remove(userId)
    }

    fun notifySSE(game: GameEntity) {
        val sses = sses[game.id] ?: return
        sses.forEach {
            Executors.newSingleThreadExecutor().execute(thread {
                val gameResponse = gameMapper.toResponse(game, null)
                it.send(objectMapper.writeValueAsString(gameResponse))
            })
        }
    }

}
