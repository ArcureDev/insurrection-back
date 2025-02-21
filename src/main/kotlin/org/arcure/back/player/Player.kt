package org.arcure.back.player

import com.fasterxml.jackson.annotation.JsonIgnore
import fr.arcure.uniting.configuration.security.CustomUser
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.arcure.back.config.WebSocketHandler
import org.arcure.back.config.annotation.IsMyPlayer
import org.arcure.back.flag.FlagEntity
import org.arcure.back.flag.FlagMapper
import org.arcure.back.flag.FlagResponse
import org.arcure.back.game.GameEntity
import org.arcure.back.game.GameRepository
import org.arcure.back.game.GameService
import org.arcure.back.getMyPlayer
import org.arcure.back.token.*
import org.arcure.back.user.UserEntity
import org.hibernate.annotations.Type
import org.springframework.context.annotation.Lazy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@Entity
@Table(name = "player")
class PlayerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    var color: String? = null,
    @Enumerated(EnumType.STRING)
    var role: PlayerRole? = null,
    @JsonIgnore
    @ManyToOne
    var game: GameEntity? = null,
    @ManyToOne
    var user: UserEntity? = null,
    @Type(JsonBinaryType::class)
    @Column(columnDefinition = "jsonb")
    var roles: List<PlayerRole> = mutableListOf(),
    @OneToMany(mappedBy = "player", cascade = [(CascadeType.ALL)], fetch = FetchType.EAGER)
    var playableTokens: MutableList<TokenEntity> = mutableListOf(),
    @OneToMany(mappedBy = "owner", cascade = [(CascadeType.ALL)], fetch = FetchType.EAGER)
    var myTokens: MutableList<TokenEntity> = mutableListOf(),
    @OneToMany(mappedBy = "player", cascade = [(CascadeType.ALL)], fetch = FetchType.EAGER)
    var flags: MutableList<FlagEntity> = mutableListOf()
)

enum class PlayerRole {
    POUVOIR, ORDRE, ECHO, PEUPLE, PAMPHLET, MOLOTOV, ECUSSON, ETOILE
}

@Repository
interface PlayerRepository : JpaRepository<PlayerEntity, Long> {
    fun findByGameIdAndUserId(gameId: Long, userId: Long): PlayerEntity?
    fun findByGameIdAndId(gameId: Long, playerId: Long): PlayerEntity?
}

class PlayerPayload(val name: String, val color: String)

class PlayerResponse(
    val id: Long,
    val name: String,
    val color: String,
    val role: PlayerRole?,
    val userId: Long,
    val playableTokens: List<TokenResponse> = mutableListOf(),
    val myTokens: List<TokenResponse> = mutableListOf(),
    val flags: List<FlagResponse> = mutableListOf(),
    val isMe: Boolean,
    val roles: List<PlayerRole>
)

class SimplePlayerResponse(
    val id: Long,
    val name: String,
    val color: String,
)

@Component
class PlayerMapper(
    private val tokenMapper: TokenMapper,
    @Lazy private val flagMapper: FlagMapper
) {
    fun toEntity(playerPayload: PlayerPayload): PlayerEntity {
        val player = PlayerEntity()
        player.name = playerPayload.name
        player.color = playerPayload.color
        return player
    }

    fun toSimpleResponse(player: PlayerEntity?): SimplePlayerResponse? {
        if (player == null) {
            return null
        }
        return SimplePlayerResponse(
            player.id!!,
            player.name!!,
            player.color!!
        )
    }

    fun toResponse(player: PlayerEntity, isMe: Boolean): PlayerResponse {
        return PlayerResponse(
            player.id!!,
            player.name!!,
            player.color!!,
            player.role,
            player.user!!.id!!,
            player.playableTokens.map { tokenMapper.toResponse(it) },
            player.myTokens.map { tokenMapper.toResponse(it) },
            player.flags.map { flagMapper.toResponse(it) },
            isMe,
            player.roles
        )
    }
}


@Service
class PlayerService(
    private val playerRepository: PlayerRepository,
    private val gameRepository: GameRepository,
    private val tokenRepository: TokenRepository,
    private val gameService: GameService,
    private val tokenMapper: TokenMapper,
) {

    @Transactional
    fun giveToken(gameId: Long, playerId: Long) {
        val player = playerRepository.getReferenceById(playerId)
        val myPlayer = getMyPlayer(gameId)

        val token = getTokenToGive(myPlayer, playerId) ?: throw EntityNotFoundException()
        myPlayer.playableTokens.remove(token)

        token.player = player
        player.playableTokens.add(token)

        playerRepository.save(myPlayer)
        playerRepository.save(player)
    }

    @Transactional
    fun changeColor(gameId: Long, color: Color) {
        val game = gameRepository.getReferenceById(gameId)
        val myPlayer = getMyPlayer(game)

        myPlayer.color = color.color
        gameRepository.save(game)
    }

    @Transactional
    fun saveRoles(gameId: Long, roles: List<PlayerRole>) {
        val player = this.gameService.getGameAndMyPlayer().player ?: return
        player.roles = roles
        playerRepository.save(player)
    }

    fun getTokens(playerId: Long): List<TokenResponse> {
        return tokenRepository.findAllByPlayerId(playerId).map { tokenMapper.toResponse(it) }
    }

    /**
     * Take other player's token in priority, connected user's otherwise
     */
    private fun getTokenToGive(myPlayer: PlayerEntity, otherPlayerId: Long): TokenEntity? {
        val token = myPlayer.playableTokens.find { it.owner?.id == otherPlayerId && it.type === TokenType.INFLUENCE }
        if (token == null) {
            return myPlayer.playableTokens.find { it.owner?.id == myPlayer.id && it.type === TokenType.INFLUENCE }
        }
        return token
    }

    private fun getMyPlayer(gameId: Long): PlayerEntity {
        val connectedUserId = CustomUser.get().userId
        return playerRepository.findByGameIdAndUserId(gameId, connectedUserId) ?: throw EntityNotFoundException()
    }

}

class Color(var color: String)

@RestController
@RequestMapping("/api")
class PlayerController(
    private val playerService: PlayerService,
    private val webSocketHandler: WebSocketHandler,
) {

    @PostMapping("/games/{gameId}/players/{playerId}/tokens")
    fun giveToken(
        @PathVariable("gameId") gameId: Long, @PathVariable("playerId") playerId: Long
    ) {
        this.playerService.giveToken(gameId, playerId)
        this.webSocketHandler.getGameAndNotify(gameId)
    }

    @IsMyPlayer
    @PostMapping("/games/{gameId}/players/{playerId}/roles")
    fun saveRoles(
        @PathVariable("gameId") gameId: Long,
        @PathVariable("playerId") playerId: Long,
        @RequestBody playerRoles: List<PlayerRole>
    ) {
        playerService.saveRoles(gameId, playerRoles)
    }

    @PostMapping("/games/{gameId}/players/color")
    fun giveToken(
        @PathVariable("gameId") gameId: Long, @RequestBody color: Color
    ) {
        playerService.changeColor(gameId, color)
        this.webSocketHandler.getGameAndNotify(gameId)
    }

    @GetMapping("/players/{playerId}/tokens")
    fun getShardTokens(@PathVariable("playerId") playerId: Long): List<TokenResponse> {
        return playerService.getTokens(playerId)
    }

}