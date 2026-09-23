package at.to2.back.admin

import at.to2.back.owner.Owner
import at.to2.back.owner.OwnerRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Sample Owners a developer can sign in as without going through Kakao.
 *
 * Guarded by the development profile, and written by a runner rather than a
 * migration for two reasons: hashing a password needs the encoder, which SQL
 * cannot reach, and a migration would carry these accounts into production
 * whether or not anyone wanted them there (ADR 0005).
 */
@Configuration
@Profile("dev")
class DevelopmentSeed {

    @Bean
    fun sampleOwners(
        accounts: LocalAccountRepository,
        owners: OwnerRepository,
        encoder: PasswordEncoder,
    ) = ApplicationRunner {
        listOf("user1", "user2", "user3").forEach { username ->
            if (accounts.findByUsername(username) != null) return@forEach

            // A synthetic Kakao subject rather than a null one: the column stays
            // NOT NULL so "one Kakao account = one Owner" holds structurally,
            // and these values cannot collide with a real Kakao subject.
            val owner =
                owners.save(Owner(kakaoSub = "dev:$username", nickname = username))

            accounts.save(
                LocalAccount(
                    username = username,
                    // Development-only credentials; never reachable in production
                    // because this whole configuration is profile-guarded.
                    passwordHash = requireNotNull(encoder.encode(username)),
                    operator = false,
                    ownerId = owner.id,
                )
            )
        }
    }
}
