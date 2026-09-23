package at.to2.back.admin

import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

@Configuration
class AdminSecurityConfig {

    /**
     * Delegating encoder: the stored hash carries its own algorithm prefix, so
     * moving off bcrypt later does not invalidate existing passwords.
     */
    @Bean fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /**
     * The Operator's chain, and the reason there are two.
     *
     * Merging this into the public chain would make behaviour depend on the
     * order the configurers happen to be declared in: with a single registered
     * OAuth2 client the entry point redirects straight to Kakao, so a password
     * form on the same chain can become unreachable without any error. Scoping
     * this one to its own path removes that coupling entirely (ADR 0005).
     *
     * Ordered ahead of the public chain, which must stay last because it matches
     * any request.
     */
    @Bean
    @Order(1)
    fun adminChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher("/admin/**"))
            .authorizeHttpRequests {
                it.requestMatchers("/admin/login")
                    .permitAll()
                    .anyRequest()
                    .hasRole(ROLE_OPERATOR)
            }
            .formLogin {
                it.loginPage("/admin/login")
                    .loginProcessingUrl("/admin/login")
                    // Where you land depends on what you are. Sending everyone to
                    // the console put a signed-in development user straight onto
                    // a 403, which made the seeded accounts useless for the one
                    // thing they exist for.
                    .successHandler { _, response, authentication ->
                        val isOperator =
                            authentication.authorities.any { a -> a.authority == "ROLE_$ROLE_OPERATOR" }
                        response.sendRedirect(if (isOperator) "/admin" else "/me")
                    }
                    .failureUrl("/admin/login?error")
            }
            .logout { it.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login") }
        return http.build()
    }

    /**
     * Creates or updates the Operator from the environment. The password never
     * appears in a migration or in the repository — only in the environment of
     * the process that needs it.
     */
    @Bean
    fun operatorProvisioner(
        accounts: LocalAccountRepository,
        encoder: PasswordEncoder,
        props: AdminProperties,
    ) = ApplicationRunner {
        if (props.password.isBlank()) return@ApplicationRunner
        val hash = requireNotNull(encoder.encode(props.password))
        val existing = accounts.findByUsername(props.username)
        if (existing == null) {
            accounts.save(
                LocalAccount(
                    username = props.username,
                    passwordHash = hash,
                    operator = true,
                )
            )
        } else {
            existing.passwordHash = hash
            existing.operator = true
            existing.lockedUntil = null
            existing.failedAttempts = 0
            accounts.save(existing)
        }
    }
}
