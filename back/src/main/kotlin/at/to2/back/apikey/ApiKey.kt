package at.to2.back.apikey

import at.to2.back.owner.Owner
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "api_key")
class ApiKey(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: Owner,
    @Column(nullable = false, length = 80) val name: String,
    @Column(name = "key_hash", nullable = false, unique = true, length = 64) val keyHash: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "api_key_id_seq")
    @SequenceGenerator(name = "api_key_id_seq", sequenceName = "api_key_id_seq", allocationSize = 1)
    var id: Long? = null

    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
    @Column(name = "revoked_at") var revokedAt: Instant? = null
}

interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    fun findAllByOwnerIdOrderByIdDesc(ownerId: Long): List<ApiKey>
    fun findByIdAndOwnerId(id: Long, ownerId: Long): ApiKey?
    fun findByKeyHashAndRevokedAtIsNull(keyHash: String): ApiKey?
}
