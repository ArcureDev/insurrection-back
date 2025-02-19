package org.arcure.back

import fr.arcure.uniting.configuration.security.CustomUser
import org.arcure.back.game.GameEntity
import org.arcure.back.player.PlayerEntity
import org.arcure.back.player.PlayerRole

fun getMyPlayer(gameEntity: GameEntity): PlayerEntity {
    val myPlayer = gameEntity.players.find { it.user?.id == CustomUser.get().userId }

    check(myPlayer != null) {
        "My player doesn't exist"
    }

    return myPlayer
}

fun buildMatrix(players: List<PlayerEntity>): List<List<Int>> {
    val roles = PlayerRole.entries.toList()

    return players.map {
        val prefs = MutableList(8) { 0 }

        it.roles.forEachIndexed { index, role ->
            val pos = roles.indexOf(role)
            prefs[pos] = index
        }
        prefs
    }
}

fun matrixToString(matrix: List<List<Int>>): String {
    val builder = StringBuilder()
    builder.append("   ")
    for (row in matrix[0].indices) {
        builder.append(row).append(" ")
    }
    for (row in matrix.indices) {
        builder.append("\n").append(row).append(" [")
        for (col in matrix[row].indices) {
            builder.append(matrix[row][col])
            if (col != matrix[row].size - 1) {
                builder.append(",")
            }
        }
        builder.append("]")
    }
    builder.append("\n")
    return builder.toString()
}