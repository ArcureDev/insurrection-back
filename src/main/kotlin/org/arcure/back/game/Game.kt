package org.arcure.back.game

import fr.arcure.uniting.configuration.security.CustomUser
import jakarta.persistence.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import org.arcure.back.buildMatrix
import org.arcure.back.config.WebSocketHandler
import org.arcure.back.config.annotation.IsMyGame
import org.arcure.back.flag.FlagMapper
import org.arcure.back.flag.FlagResponse
import org.arcure.back.matrixToString
import org.arcure.back.player.*
import org.arcure.back.token.NB_SHARD_TOKENS_MAX
import org.arcure.back.token.NB_SHARD_TOKENS_START
import org.arcure.back.token.TokenEntity
import org.arcure.back.token.TokenType
import org.arcure.back.user.UserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.*
import java.util.stream.IntStream
import kotlin.math.min
import kotlin.streams.toList

@Entity
@Table(name = "game")
class GameEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Max(value = 28)
    var nbAvailableShardTokens: Int = NB_SHARD_TOKENS_MAX - NB_SHARD_TOKENS_START,
    var nbVotes: Int = 0,
    @OneToMany(mappedBy = "game", cascade = [(CascadeType.ALL)], fetch = FetchType.LAZY, orphanRemoval = true)
    var players: MutableList <PlayerEntity> = mutableListOf(),
    @Enumerated(EnumType.STRING)
    var state: GameState = GameState.START,
    var url: String = UUID.randomUUID().toString()
)

enum class GameState {
    START, ON_GOING, DONE
}

@Repository
interface GameRepository : JpaRepository<GameEntity, Long> {
    @Query("""
        SELECT g.id, g.nb_available_shard_tokens, g.nb_votes, g.state, g.url
        FROM game g
        INNER JOIN player p ON p.game_id = g.id
        WHERE p.user_id = :userId
    """, nativeQuery = true)
    fun findAllByPlayers(userId: Long): MutableList<GameEntity>

    @Query("""
        SELECT g.id, g.nb_available_shard_tokens, g.nb_votes, g.state, g.url
        FROM game g
        INNER JOIN player p ON p.game_id = g.id
        WHERE p.user_id = :userId
        AND g.state <> 'DONE'
        LIMIT 1
    """, nativeQuery = true)
    fun findCurrent(userId: Long): GameEntity?

    fun findByUrl(url: String): GameEntity?
}

class GameResponse(
    val id: Long,
    val nbAvailableShardTokens: Int,
    val nbVotes: Int,
    val players: List <PlayerResponse> = mutableListOf(),
    val state: GameState,
    val url: String,
    val flags: List<FlagResponse> = mutableListOf()
)

@Component
class GameMapper(private val playerMapper: PlayerMapper, private val flagMapper: FlagMapper) {
    fun toResponse(game: GameEntity, myPlayer: PlayerEntity?): GameResponse {
        val players = game.players.map { playerMapper.toResponse(it, it.id == myPlayer?.id) }.toMutableList()
        val flags = game.players.flatMap { it.flags }.map { flagMapper.toResponse(it) }

        return GameResponse(
            game.id!!,
            game.nbAvailableShardTokens,
            game.nbVotes,
            players,
            game.state,
            game.url,
            flags
        )
    }
}

@Service
class GameService(
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
    private val gameMapper: GameMapper,
) {

    @Transactional
    fun create(playerPayload: PlayerPayload): GameResponse {
        check (gameRepository.findCurrent(CustomUser.get().userId) == null) {
            "Game already exists"
        }

        val game = GameEntity()
        val myPlayer = getMyPlayer(game, playerPayload)
        game.players.add(myPlayer)
        gameRepository.save(game)

        return gameMapper.toResponse(game, myPlayer)
    }

    fun getGame(gameId: Long): GameResponse {
        return gameMapper.toResponse(gameRepository.getReferenceById(gameId), null)
    }

    fun getAllMine(): List<GameEntity> {
        return gameRepository.findAllByPlayers(CustomUser.get().userId)
    }

    fun getCurrentGame(): GameResponse {
        val gameAndMyPlayer = getGameAndMyPlayer()
        return gameMapper.toResponse(gameAndMyPlayer.game, gameAndMyPlayer.player)
    }

    fun getMyGameOrGame(gameId: Long?): GameResponse {
        if (CustomUser.getOrNull() == null) {
            if (gameId == null) throw EntityNotFoundException("Game not found")
            return getGame(gameId)
        }
        return getCurrentGame()
    }

    @Transactional
    fun join(url: String, playerPayload: PlayerPayload) {
        val game = gameRepository.findByUrl(url)
        check (game != null) {
            "Game not exists"
        }

        val myPlayer = getMyPlayer(game, playerPayload)
        game.players.add(myPlayer)
        gameRepository.save(game)
    }

    @Transactional
    fun closeGame(id: Long) {
        val game = gameRepository.getReferenceById(id)
        game.state = GameState.DONE
        gameRepository.save(game)
    }

    @Transactional
    fun quitGame(id: Long) {
        val gameAndMyPlayer = getGameAndMyPlayer()
        val game = gameAndMyPlayer.game
        game.players.remove(gameAndMyPlayer.player)
        gameRepository.save(game)
    }
    
    @Transactional
    fun addVote() {
        val game = getGame()
        game.nbVotes++
        gameRepository.save(game)
    }

    @Transactional
    fun resetVote() {
        val game = getGame()
        game.nbVotes = 0
        gameRepository.save(game)
    }
    
    fun getGameAndMyPlayer(): GameAndMyPlayer {
        val game = getGame()
        val myPlayer = game.players.find { it.user?.id == CustomUser.get().userId }

        check(myPlayer != null) {
            "No current player"
        }

        return GameAndMyPlayer(game, myPlayer)
    }

    @Transactional
    fun assignRoles_Navelji(gameId: Long) {
        val game = gameRepository.getReferenceById(gameId)
        val players = game.players
        var minimum = Int.MAX_VALUE
        val matrix = buildMatrix(players)
        println(matrixToString(matrix))
        var tab = IntStream.range(0, matrix.size).toList()
        val permutations = generatePermutations_Navelji(IntStream.range(0, 8).toList())
        for (permutation in permutations) {
            var score = 0
            for (row in matrix.indices) {
                score += matrix[row][permutation[row]]
            }
            minimum = min(minimum, score)
            if (minimum == score) {
                tab = permutation
            }
        }

        tab.forEachIndexed { index, i ->
            if (index < matrix.size) {
                val role = PlayerRole.entries[i]
                players[index].role = role
                println("user $index plays $role")
            }
        }

        gameRepository.save(game)
    }

    private fun generatePermutations_Navelji(elems: List<Int>): List<List<Int>> {
        if (elems.size == 1) {
            return listOf(elems)
        }

        val permutations = mutableListOf<List<Int>>()
        for (idx in elems.indices) {
            val elem = elems[idx]
            val remaining = elems.slice(0 until idx) + elems.slice(idx + 1 until elems.size)
            for (perm in generatePermutations_Navelji(remaining)) {
                permutations.add(listOf(elem) + perm)
            }
        }
        return permutations
    }

    private fun getMyPlayer(game: GameEntity, playerPayload: PlayerPayload): PlayerEntity {
        return game.players.find { it.user?.id == CustomUser.get().userId } ?: generateMyPlayer(game, playerPayload)
    }
    
    private fun generateMyPlayer(game: GameEntity, playerPayload: PlayerPayload): PlayerEntity {
        val user = userRepository.getReferenceById(CustomUser.get().userId)
        val playerEntity = PlayerEntity()
        playerEntity.user = user
        playerEntity.game = game
        playerEntity.name = playerPayload.name
        playerEntity.color = playerPayload.color

        playerEntity.myTokens = mutableListOf(
            TokenEntity(null, TokenType.INFLUENCE, playerEntity, playerEntity),
            TokenEntity(null, TokenType.INFLUENCE, playerEntity, playerEntity),
            TokenEntity(null, TokenType.INFLUENCE, playerEntity, playerEntity)
        )

        return playerEntity
    }

    private fun getGame(): GameEntity {
        return gameRepository.findCurrent(CustomUser.get().userId) ?: throw EntityNotFoundException()
    }

    class GameAndMyPlayer(val game: GameEntity, val player: PlayerEntity?)

}

@RestController
@RequestMapping("/api/games")
class GameController(
    private val gameService: GameService, private val webSocketHandler: WebSocketHandler,
) {
    @GetMapping("/{gameId}")
    fun getGame(@PathVariable gameId: Long): GameResponse {
        return this.gameService.getGame(gameId)
    }

    @GetMapping("/me")
    fun getAllMine(): List<GameEntity> {
        return this.gameService.getAllMine()
    }

    @GetMapping("/me/current")
    fun getCurrent(): GameResponse? {
        return this.gameService.getCurrentGame()
    }

    @PostMapping
    fun create(@RequestBody @Valid player: PlayerPayload): GameResponse {
        return this.gameService.create(player)
    }

    @PutMapping
    fun join(@RequestParam url: String, @RequestBody @Valid player: PlayerPayload): GameResponse {
        this.gameService.join(url, player)
        return this.webSocketHandler.getGameAndNotify(null)
    }

    @IsMyGame
    @PostMapping("/me/votes")
    fun addVote() {
        this.gameService.addVote()
        this.webSocketHandler.getGameAndNotify(null)
    }

    @IsMyGame
    @DeleteMapping("/me/votes")
    fun resetVote() {
        this.gameService.resetVote()
        this.webSocketHandler.getGameAndNotify(null)
    }

    @IsMyGame
    @DeleteMapping("/{gameId}")
    fun close(@PathVariable gameId: Long) {
        this.gameService.closeGame(gameId)
        this.webSocketHandler.getGameAndNotify(gameId)
    }

    @IsMyGame
    @DeleteMapping("/{gameId}/players")
    fun quit(@PathVariable gameId: Long) {
        this.gameService.quitGame(gameId)
        this.webSocketHandler.getGameAndNotify(gameId)
    }

    @IsMyGame
    @GetMapping("/{gameId}/roles")
    fun assignRoles(@PathVariable gameId: Long) {
        this.gameService.assignRoles_Navelji(gameId)
        this.webSocketHandler.getGameAndNotify(gameId)
    }

}
