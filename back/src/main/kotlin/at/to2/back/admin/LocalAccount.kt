package at.to2.back.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

/**
 * A username/password account.
 *
 * The Operator is one of these and has no [ownerId]: administration and
 * ownership stay separate, so the Operator cannot create Links (ADR 0005).
 * Development seed accounts also live here and do point at an Owner.
 */
@Entity
@Table(name = "local_account")
class LocalAccount(
    @Column(nullable = false, unique = true) var username: String,
    /** Carries its algorithm prefix so the hash can be migrated later. */
    @Column(name = "password_hash", nullable = false) var passwordHash: String,
    @Column(nullable = false) var operator: Boolean = false,
    @Column(name = "owner_id") var ownerId: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "local_account_id_seq")
    @SequenceGenerator(
        name = "local_account_id_seq",
        sequenceName = "local_account_id_seq",
        allocationSize = 1,
    )
    var id: Long? = null

    @Column(name = "failed_attempts", nullable = false) var failedAttempts: Int = 0

    @Column(name = "locked_until") var lockedUntil: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    fun isLocked(now: Instant = Instant.now()): Boolean = lockedUntil?.isAfter(now) == true
}

interface LocalAccountRepository : JpaRepository<LocalAccount, Long> {
    fun findByUsername(username: String): LocalAccount?
}
