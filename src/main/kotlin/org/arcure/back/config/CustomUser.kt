package fr.arcure.uniting.configuration.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import java.util.*

class CustomUser(private val email: String, private val password: String, val userId: Long) : UserDetails {

    companion object {
        fun get(): CustomUser {
            val authentication = SecurityContextHolder.getContext().authentication
            checkNotNull(authentication) { "no Authentication found" }

            return getCustomerUser(authentication)
        }

        fun getOrNull(): CustomUser? {
            val authentication = SecurityContextHolder.getContext().authentication ?: return null
            return getCustomerUser(authentication)
        }

        private fun getCustomerUser(authentication: Authentication): CustomUser {
            val principal = authentication.principal
            check (principal is CustomUser) { "no principal" }
            return principal
        }
    }

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        return mutableListOf()
    }

    override fun getPassword(): String {
        return password
    }

    override fun getUsername(): String {
        return email
    }
}