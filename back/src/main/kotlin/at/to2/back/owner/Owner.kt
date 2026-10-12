package at.to2.back.owner

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.LockModeType
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * A person who creates Links, identified by their Kakao account.
 *
 * An Operator is not an Owner and has no row here.
 */
@Entity
@Table(name = "owner")
class Owner(
    /**
     * The Kakao OIDC subject. Immutable, and the only identity we key on: email
     * is optional even on a Biz app, so it must never be the identifier.
     */
    @Column(name = "kakao_sub", nullable = false, updatable = false) var kakaoSub: String,
    @Column(nullable = false) var nickname: String,
    @Column var email: String? = null,
    @Column(name = "email_verified", nullable = false) var emailVerified: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "owner_id_seq")
    @SequenceGenerator(name = "owner_id_seq", sequenceName = "owner_id_seq", allocationSize = 1)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

interface OwnerRepository : JpaRepository<Owner, Long> {
    fun findByKakaoSub(kakaoSub: String): Owner?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Owner o where o.id = :id")
    fun lockForLinkCreation(@Param("id") id: Long): Owner
}
