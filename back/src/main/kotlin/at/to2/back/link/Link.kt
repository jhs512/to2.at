package at.to2.back.link

import at.to2.back.owner.Owner
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

enum class LinkState {
    ACTIVE,

    /** Removed by its Owner. The row stays so the Code is never reissued. */
    DELETED,

    /** Stopped by an Operator. The Target must not be disclosed. */
    SUSPENDED,
}

/**
 * Renders and parses Codes. The only place that knows how a Code is spelled, so
 * the scheme can be swapped later without touching the schema (ADR 0002).
 */
object Code {
    fun render(id: Long): String = id.toString()

    /**
     * Returns null unless [raw] is the canonical spelling of a Code. Rejecting
     * non-canonical forms keeps one Link from being reachable at `/1`, `/01`
     * and `/0000001` alike.
     */
    fun parse(raw: String): Long? {
        val id = raw.toLongOrNull() ?: return null
        if (id < 1) return null
        return if (render(id) == raw) id else null
    }
}

@Entity
@Table(name = "link")
class Link(
    @Column(nullable = false) var target: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: Owner,
) {
    /**
     * The primary key IS the Code. Allocation size is 1 on purpose: the default
     * of 50 leaves gaps whenever the app restarts mid-block, and redeploys are
     * frequent (ADR 0002).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "link_code_seq")
    @SequenceGenerator(name = "link_code_seq", sequenceName = "link_code_seq", allocationSize = 1)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: LinkState = LinkState.ACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "state_changed_at") var stateChangedAt: Instant? = null

    val code: String
        get() = Code.render(checkNotNull(id) { "Link has not been persisted yet" })

    fun changeState(next: LinkState) {
        state = next
        stateChangedAt = Instant.now()
    }
}

interface LinkRepository : JpaRepository<Link, Long> {
    fun findAllByOwnerIdOrderByIdDesc(ownerId: Long): List<Link>

    // Update only Target: a concurrent Suspension or deletion must survive.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE link SET target = :target WHERE id = :id AND owner_id = :ownerId AND state <> 'DELETED'",
        nativeQuery = true,
    )
    fun updateOwnedTarget(
        @Param("id") id: Long,
        @Param("ownerId") ownerId: Long,
        @Param("target") target: String,
    ): Int
}
