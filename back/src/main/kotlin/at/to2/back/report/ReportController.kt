package at.to2.back.report

import at.to2.back.link.Code
import at.to2.back.link.LinkRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CreateReportRequest(val code: String = "", val reason: String? = null)

data class ReportAccepted(val accepted: Boolean = true)

@RestController
class ReportController(
    private val reports: ReportRepository,
    private val links: LinkRepository,
    private val suspension: AutoSuspensionPolicy,
) {

    /**
     * Anyone can file a Report — no account, no barrier. The Visitor who just
     * saw where a Link goes is the person best placed to flag it.
     */
    @PostMapping("/reports")
    @Transactional
    fun create(
        @RequestBody request: CreateReportRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ReportAccepted> {
        val id = Code.parse(request.code) ?: return ResponseEntity.notFound().build()
        val link = links.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()

        val ip = servletRequest.remoteAddr ?: "unknown"

        // Repeat Reports from one source are accepted but not stored again. The
        // Visitor gets the same confirmation either way — telling them "you
        // already reported this" only invites them to try from somewhere else.
        if (!reports.existsByLinkIdAndReporterIp(id, ip)) {
            reports.save(Report(link = link, reporterIp = ip, reason = request.reason?.take(500)))
            suspension.onReportFiled(link)
        }

        return ResponseEntity.accepted().body(ReportAccepted())
    }
}
