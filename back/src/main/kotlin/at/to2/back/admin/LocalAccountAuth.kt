package at.to2.back.admin

import java.time.Duration
import java.time.Instant
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

const val ROLE_OPERATOR = "OPERATOR"

@ConfigurationProperties(prefix = "to2.admin")
data class AdminProperties(
    /**
     * The Operator's password, supplied by the environment only. Never committed
     * and never written into a migration.
     */
    val password: String = "",
    val username: String = "admin",
    val maxFailedAttempts: Int = 5,
    val lockDuration: Duration = Duration.ofMinutes(15),
)

@Service
class LocalAccountDetailsService(private val accounts: LocalAccountRepository) :
    UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val account =
            accounts.findByUsername(username) ?: throw UsernameNotFoundException(username)

        return User.withUsername(account.username)
            .password(account.passwordHash)
            .authorities(
                if (account.operator) listOf(SimpleGrantedAuthority("ROLE_$ROLE_OPERATOR"))
                else emptyList()
            )
            // The framework ships no brute-force protection; this flag is the
            // hook that turns our own counter into a refused login.
            .accountLocked(account.isLocked())
            .build()
    }
}

/**
 * Lockout, built by hand because the framework has no such feature.
 *
 * Failures are counted per account and cleared on success. Without this a single
 * password guards the ability to stop and restore anybody's Links.
 */
@Component
class LoginAttemptTracker(
    private val accounts: LocalAccountRepository,
    private val props: AdminProperties,
) {

    @EventListener
    @Transactional
    fun onFailure(event: AuthenticationFailureBadCredentialsEvent) {
        val account = accounts.findByUsername(event.authentication.name ?: return) ?: return
        account.failedAttempts += 1
        if (account.failedAttempts >= props.maxFailedAttempts) {
            account.lockedUntil = Instant.now().plus(props.lockDuration)
            account.failedAttempts = 0
        }
    }

    @EventListener
    @Transactional
    fun onSuccess(event: AuthenticationSuccessEvent) {
        val account = accounts.findByUsername(event.authentication.name ?: return) ?: return
        account.failedAttempts = 0
        account.lockedUntil = null
    }
}
