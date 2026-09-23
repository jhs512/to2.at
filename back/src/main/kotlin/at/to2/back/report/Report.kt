package at.to2.back.report

import at.to2.back.link.Link
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
import org.springframework.data.jpa.repository.Query

@Entity
@Table(name = "report")
class Report(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    var link: Link,
    @Column(name = "reporter_ip", nullable = false) var reporterIp: String,
    @Column var reason: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "report_id_seq")
    @SequenceGenerator(name = "report_id_seq", sequenceName = "report_id_seq", allocationSize = 1)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "handled_at") var handledAt: Instant? = null
}

interface ReportRepository : JpaRepository<Report, Long> {
    fun existsByLinkIdAndReporterIp(linkId: Long, reporterIp: String): Boolean

    fun countByLinkId(linkId: Long): Long

    @Query("select r from Report r join fetch r.link order by r.createdAt desc")
    fun findAllNewestFirst(): List<Report>
}
