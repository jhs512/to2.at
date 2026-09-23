package at.to2.back.report

import at.to2.back.link.Link
import at.to2.back.link.LinkState
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "to2.moderation")
data class ModerationProperties(
    /**
     * Reports from this many distinct sources suspend a Link without waiting for
     * the Operator. A safety net for the days nobody is looking, not the primary
     * path — the Operator reviewing and acting is.
     */
    val autoSuspendAfterReports: Int = 3
)

@Component
class AutoSuspensionPolicy(
    private val reports: ReportRepository,
    private val props: ModerationProperties,
) {

    fun onReportFiled(link: Link) {
        if (link.state != LinkState.ACTIVE) return

        // Reports are unique per source per Link, so this count is a count of
        // distinct sources — one person cannot reach the threshold alone.
        if (reports.countByLinkId(link.id!!) >= props.autoSuspendAfterReports) {
            link.changeState(LinkState.SUSPENDED)
        }
    }
}
