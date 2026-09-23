package at.to2.back.admin

import at.to2.back.link.LinkState
import at.to2.back.report.Report
import at.to2.back.report.ReportRepository
import at.to2.back.support.IntegrationTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@TestPropertySource(properties = ["to2.admin.password="])
@WithMockUser(username = "admin", roles = [ROLE_OPERATOR])
class SuspensionTest : IntegrationTest() {

    @Autowired private lateinit var reports: ReportRepository

    @BeforeEach
    fun reset() {
        reports.deleteAll()
        clearAll()
    }

    @Test
    fun `the Operator sees reported Links with their Code, Target and report count`() {
        val link = aLink(target = "https://example.com/bad")
        reports.save(Report(link = link, reporterIp = "1.1.1.1", reason = "피싱"))
        reports.save(Report(link = link, reporterIp = "2.2.2.2"))

        mvc.get("/admin").andExpect {
            status { isOk() }
            content { string(containsString(link.code)) }
            content { string(containsString("https://example.com/bad")) }
            content { string(containsString("신고 2건")) }
            content { string(containsString("피싱")) }
        }
    }

    @Test
    fun `the Operator stops a Link in one action`() {
        val link = aLink(target = "https://example.com/bad")

        mvc.post("/admin/links/${link.code}/suspend") { with(csrf()) }
            .andExpect { status { is3xxRedirection() } }

        mvc.get("/to/${link.code}").andExpect { jsonPath("$.state") { value("SUSPENDED") } }
    }

    @Test
    fun `a suspended Link never shows its Target to a Visitor`() {
        val link = aLink(target = "https://example.com/bad", state = LinkState.SUSPENDED)

        mvc.get("/to/${link.code}").andExpect {
            jsonPath("$.state") { value("SUSPENDED") }
            // Showing it would advertise exactly the destination the Suspension
            // exists to stop.
            jsonPath("$.target") { doesNotExist() }
        }
    }

    @Test
    fun `a Suspension can be lifted`() {
        val link = aLink(state = LinkState.SUSPENDED)

        mvc.post("/admin/links/${link.code}/restore") { with(csrf()) }

        mvc.get("/to/${link.code}").andExpect { jsonPath("$.state") { value("ACTIVE") } }
    }

    @Test
    fun `a Link can be stopped without anyone having reported it`() {
        val link = aLink()

        mvc.post("/admin/links/suspend") {
            param("code", link.code)
            with(csrf())
        }

        mvc.get("/to/${link.code}").andExpect { jsonPath("$.state") { value("SUSPENDED") } }
    }

    @Test
    fun `restoring never resurrects a Link its Owner deleted`() {
        val link = aLink(state = LinkState.DELETED)

        mvc.post("/admin/links/${link.code}/restore") { with(csrf()) }

        mvc.get("/to/${link.code}").andExpect { jsonPath("$.state") { value("DELETED") } }
    }

    @Test
    fun `a Report can be marked handled so the queue shows what is left`() {
        val link = aLink()
        val report = reports.save(Report(link = link, reporterIp = "1.1.1.1"))

        mvc.post("/admin/reports/${report.id}/handled") { with(csrf()) }

        mvc.get("/admin").andExpect { content { string(containsString("처리됨")) } }
    }

    @Test
    fun `the Owner sees their Link marked as suspended`() {
        val owner = anOwner(kakaoSub = "kakao-1")
        aLink(owner = owner, state = LinkState.SUSPENDED)

        mvc.get("/links") { with(asOwner("kakao-1")) }
            .andExpect { jsonPath("$[0].state") { value("SUSPENDED") } }
    }

    @Test
    fun `the console never leaks into the public surface`() {
        val link = aLink(target = "https://example.com/bad", state = LinkState.SUSPENDED)

        mvc.get("/to/${link.code}").andExpect {
            content { string(not(containsString("example.com"))) }
        }
    }
}
