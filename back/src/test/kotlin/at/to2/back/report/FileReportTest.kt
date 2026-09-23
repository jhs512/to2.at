package at.to2.back.report

import at.to2.back.support.IntegrationTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class FileReportTest : IntegrationTest() {

    @Autowired private lateinit var reports: ReportRepository

    @BeforeEach
    fun reset() {
        reports.deleteAll()
        clearAll()
    }

    private fun report(code: String, from: String = "1.2.3.4", reason: String? = null) =
        mvc.post("/reports") {
            contentType = MediaType.APPLICATION_JSON
            content =
                if (reason == null) """{"code":"$code"}"""
                else """{"code":"$code","reason":"$reason"}"""
            with { it.remoteAddr = from; it }
        }

    @Test
    fun `anyone can file a Report without signing in`() {
        val link = aLink()

        report(link.code).andExpect {
            status { isAccepted() }
            jsonPath("$.accepted") { value(true) }
        }

        assertEquals(1, reports.count())
    }

    @Test
    fun `a reason is kept when given`() {
        val link = aLink()

        report(link.code, reason = "피싱 사이트").andExpect { status { isAccepted() } }

        assertEquals("피싱 사이트", reports.findAll().single().reason)
    }

    @Test
    fun `repeat Reports from one source are accepted but not counted twice`() {
        val link = aLink()

        repeat(5) { report(link.code, from = "1.2.3.4").andExpect { status { isAccepted() } } }

        // The Visitor gets the same confirmation each time — saying "you already
        // reported this" only invites them to retry from elsewhere.
        assertEquals(1, reports.count())
    }

    @Test
    fun `a Report against an unknown Code is not found`() {
        report("999999").andExpect { status { isNotFound() } }
        report("abc").andExpect { status { isNotFound() } }
    }

    @Test
    fun `filing a Report does not by itself stop the Link`() {
        val link = aLink()

        report(link.code, from = "1.1.1.1")

        // One Report is a signal, not a verdict. Stopping on a single anonymous
        // click would make any Link trivially killable by a stranger.
        mvc.get("/to/${link.code}").andExpect { jsonPath("$.state") { value("ACTIVE") } }
    }

    @Test
    fun `Reports from enough distinct sources suspend the Link`() {
        val link = aLink(target = "https://example.com/bad")

        listOf("1.1.1.1", "2.2.2.2", "3.3.3.3").forEach { report(link.code, from = it) }

        mvc.get("/to/${link.code}").andExpect {
            jsonPath("$.state") { value("SUSPENDED") }
            jsonPath("$.target") { doesNotExist() }
        }
    }

    @Test
    fun `one source cannot manufacture a Suspension alone`() {
        val link = aLink()

        repeat(10) { report(link.code, from = "1.1.1.1") }

        mvc.get("/to/${link.code}").andExpect { jsonPath("$.state") { value("ACTIVE") } }
    }
}
