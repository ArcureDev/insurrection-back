package org.arcure.back

import org.arcure.back.player.PlayerEntity
import org.arcure.back.player.PlayerRole
import org.arcure.back.player.PlayerRole.*
import org.arcure.back.player.PlayerService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.stream.IntStream

@SpringBootTest
class BackApplicationTests {

    @Autowired
    lateinit var playerService: PlayerService

    @Test
    fun wood() {
        val players = IntStream.range(0, 8).mapToObj{
            createPlayer(it, getOrderedRoles(it))
        }.toList()
    }

    @Test
    fun bronze() {
        val roles = listOf(
            listOf<PlayerRole>(ECHO, *getAllRolesExcept(ECHO).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, PEUPLE, *getAllRolesExcept(PAMPHLET, PEUPLE).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, POUVOIR, *getAllRolesExcept(PAMPHLET, POUVOIR).toTypedArray()),
        )
        val players = IntStream.range(0, roles.size).mapToObj { createPlayer(it, roles[it]) }.toList()
    }

    @Test
    fun silver() {
        val roles = listOf(
            listOf<PlayerRole>(ECHO, *getAllRolesExcept(ECHO).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, POUVOIR, *getAllRolesExcept(PAMPHLET, POUVOIR).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, PEUPLE, ECUSSON, *getAllRolesExcept(ORDRE, PEUPLE, ECUSSON).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, PEUPLE, MOLOTOV, *getAllRolesExcept(PAMPHLET, PEUPLE, MOLOTOV).toTypedArray()),
        )
        val players = IntStream.range(0, roles.size).mapToObj { createPlayer(it, roles[it]) }

    }

    @Test
    fun gold() {
        val roles = listOf(
            listOf<PlayerRole>(ECHO, *getAllRolesExcept(ECHO).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, PEUPLE, ECUSSON, *getAllRolesExcept(PAMPHLET, POUVOIR, ECUSSON).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, PEUPLE, MOLOTOV, ETOILE, *getAllRolesExcept(ORDRE, PEUPLE, MOLOTOV, ETOILE).toTypedArray()),
            listOf<PlayerRole>(
                PAMPHLET, PEUPLE, MOLOTOV, ORDRE, *getAllRolesExcept(
                    PAMPHLET, PEUPLE, MOLOTOV, ORDRE
                ).toTypedArray()),
        )
        val players = IntStream.range(0, roles.size).mapToObj { createPlayer(it, roles[it]) }

    }

    @Test
    fun plat() {
        val roles = listOf(
            listOf<PlayerRole>(ECHO, *getAllRolesExcept(ECHO).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, PEUPLE, ECUSSON, *getAllRolesExcept(PAMPHLET, POUVOIR, ECUSSON).toTypedArray()),
            listOf<PlayerRole>(PAMPHLET, PEUPLE, MOLOTOV, ETOILE, *getAllRolesExcept(ORDRE, PEUPLE, MOLOTOV, ETOILE, ORDRE).toTypedArray()),
            listOf<PlayerRole>(
                PAMPHLET, PEUPLE, MOLOTOV, ETOILE, *getAllRolesExcept(PAMPHLET, PEUPLE, MOLOTOV, ETOILE, POUVOIR).toTypedArray()),
        )
        val players = IntStream.range(0, roles.size).mapToObj { createPlayer(it, roles[it]) }

    }

    @Test
    fun diamond() {

    }

    @Test
    fun master() {

    }

    /**
     * Personne n'a modifié l'ordre => tout le monde a le même ordre D:
     */
    @Test
    fun challenger() {
        val roles = PlayerRole.entries.toTypedArray().toList()
        val players = IntStream.range(0, roles.size).mapToObj { createPlayer(it, roles)}.toList()
    }

    /* ***************************************************************************************
    *                                       UTILS
    *************************************************************************************** */

    fun createPlayer(i: Int, roles: List<PlayerRole>): PlayerEntity {
        return PlayerEntity(
            null,
            "player $i",
            null,
            null,
            null,
            null,
            roles,
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )
    }

    fun getOrderedRoles(i: Int): List<PlayerRole> {
        return PlayerRole.entries.toTypedArray().toMutableList().swap(0, i)
    }

    fun getAllRolesExcept(vararg rolesToRemove: PlayerRole): List<PlayerRole> {
        val roles = PlayerRole.entries.toTypedArray().toMutableList()
        rolesToRemove.forEach {
            roles.remove(it)
        }
        return roles.toList()
    }

    fun <T> MutableList<T>.swap(index1: Int, index2: Int): MutableList<T> {
        val tmp = this[index1]
        this[index1] = this[index2]
        this[index2] = tmp
        return this
    }
}
