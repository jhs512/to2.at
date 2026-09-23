package at.to2.back.admin

import at.to2.back.link.Code
import at.to2.back.link.LinkRepository
import at.to2.back.link.LinkState
import at.to2.back.report.ReportRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.http.MediaType
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView

private val AT_SEOUL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))

@Controller
class OperatorConsoleController(
    private val reports: ReportRepository,
    private val links: LinkRepository,
) {

    /**
     * The Report queue. There is no notification anywhere — the Operator checks
     * this page, which is the whole delivery mechanism by design.
     */
    @GetMapping("/admin", produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    @Transactional(readOnly = true)
    fun console(csrf: CsrfToken?): String {
        val rows =
            reports.findAllNewestFirst().joinToString("\n") { report ->
                val link = report.link
                val count = reports.countByLinkId(link.id!!)
                val handled = report.handledAt != null
                """
                <tr${if (handled) """ class="muted"""" else ""}>
                  <td><strong>${link.code}</strong><br>
                      <span class="pill${if (link.state == LinkState.SUSPENDED) " suspended" else ""}">${link.state}</span></td>
                  <td class="target">${escape(link.target)}<br>
                      <span class="muted">신고 ${count}건 · ${AT_SEOUL.format(report.createdAt)}
                      ${report.reason?.let { "· " + escape(it) } ?: ""}</span></td>
                  <td>
                    <div class="row-actions">
                      ${suspendButton(link.code, link.state, csrf)}
                      ${if (!handled) form("/admin/reports/${report.id}/handled", "처리완료", csrf) else """<span class="muted">처리됨</span>"""}
                    </div>
                  </td>
                </tr>
                """
            }

        return adminPage(
            "신고",
            """
            <form method="post" action="/admin/links/suspend" class="row-actions" style="margin-bottom:24px">
              ${csrfInput(csrf)}
              <input name="code" placeholder="Code" style="max-width:120px">
              <button type="submit">신고 없이 중지</button>
            </form>
            <table>
              <thead><tr><th>Code</th><th>Target / 신고</th><th></th></tr></thead>
              <tbody>${rows.ifBlank { """<tr><td colspan="3" class="muted">신고가 없습니다.</td></tr>""" }}</tbody>
            </table>
            <p class="muted" style="margin-top:24px">
              <a href="/admin/logout" onclick="event.preventDefault();document.getElementById('lo').submit()">로그아웃</a>
            </p>
            <form id="lo" method="post" action="/admin/logout">${csrfInput(csrf)}</form>
            """,
        )
    }

    /**
     * Stopping a Link, from the row action or by typing a Code. The second form
     * is reachable without any Report: there are obvious cases that should not
     * have to wait for someone to complain.
     */
    @PostMapping("/admin/links/suspend", "/admin/links/{code}/suspend")
    @Transactional
    fun suspend(
        @PathVariable(required = false) code: String?,
        @RequestParam(required = false, name = "code") typedCode: String?,
    ): RedirectView {
        changeState(code ?: typedCode ?: return RedirectView("/admin"), LinkState.SUSPENDED)
        return RedirectView("/admin")
    }

    @PostMapping("/admin/links/{code}/restore")
    @Transactional
    fun restore(@PathVariable code: String): RedirectView {
        // Suspension has to be undoable, or a misjudgement is permanent.
        changeState(code, LinkState.ACTIVE)
        return RedirectView("/admin")
    }

    @PostMapping("/admin/reports/{id}/handled")
    @Transactional
    fun markHandled(@PathVariable id: Long): RedirectView {
        reports.findById(id).ifPresent { it.handledAt = Instant.now() }
        return RedirectView("/admin")
    }

    private fun changeState(code: String, state: LinkState) {
        val id = Code.parse(code) ?: return
        links.findById(id).ifPresent { link ->
            // A deleted Link stays deleted; restoring one would resurrect an
            // address its Owner deliberately retired.
            if (link.state != LinkState.DELETED) link.changeState(state)
        }
    }

    private fun suspendButton(code: String, state: LinkState, csrf: CsrfToken?) =
        when (state) {
            LinkState.SUSPENDED -> form("/admin/links/$code/restore", "중지 해제", csrf)
            LinkState.ACTIVE -> form("/admin/links/$code/suspend", "중지", csrf)
            LinkState.DELETED -> """<span class="muted">삭제됨</span>"""
        }

    private fun form(action: String, label: String, csrf: CsrfToken?) =
        """<form method="post" action="$action">${csrfInput(csrf)}<button type="submit">$label</button></form>"""
}

private fun escape(value: String) =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
