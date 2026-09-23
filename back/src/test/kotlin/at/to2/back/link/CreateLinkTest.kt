package at.to2.back.link

import at.to2.back.support.IntegrationTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class CreateLinkTest : IntegrationTest() {

    @BeforeEach fun reset() = clearAll()

    private fun create(target: String, sub: String = "kakao-1") =
        mvc.post("/links") {
            with(asOwner(sub))
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"$target"}"""
        }

    @Test
    fun `an Owner gets a Code and a shareable short address`() {
        create("https://example.com/a/very/long/path").andExpect {
            status { isCreated() }
            jsonPath("$.code") { exists() }
            jsonPath("$.target") { value("https://example.com/a/very/long/path") }
            jsonPath("$.shortUrl") { value(org.hamcrest.Matchers.startsWith("https://to2.at/")) }
            jsonPath("$.state") { value("ACTIVE") }
        }
    }

    @Test
    fun `a new Link resolves immediately`() {
        val body = create("https://example.com/x").andReturn().response.contentAsString
        val code = Regex(""""code":"(\d+)"""").find(body)!!.groupValues[1]

        mvc.get("/to/$code").andExpect { jsonPath("$.target") { value("https://example.com/x") } }
    }

    @Test
    fun `Codes come out consecutive, with no gaps`() {
        val codes = (1..5).map { codeOf(create("https://example.com/$it")) }

        assertEquals((codes.first()..codes.first() + 4).toList(), codes)
    }

    @Test
    fun `the Code sequence increments by one, so a restart cannot leave a gap`() {
        // The gap this guards against only appears across a JVM restart, which a
        // single test run cannot stage. What it can check is the property that
        // makes the gap impossible: the sequence hands out one value at a time
        // rather than reserving a block the process might not finish (ADR 0002).
        //
        // The ORM's allocation size has to agree with this or the context fails
        // to start at all, so a passing suite pins both halves.
        val increment =
            jdbc.queryForObject(
                "SELECT increment_by FROM pg_sequences WHERE sequencename = 'link_code_seq'",
                Long::class.java,
            )

        assertEquals(1L, increment)
    }

    @Test
    fun `the same Target twice makes two Links`() {
        val first = codeOf(create("https://example.com/same"))
        val second = codeOf(create("https://example.com/same"))

        // Deliberately not deduplicated: the two may be shared in different
        // places and deleted independently.
        assertEquals(first + 1, second)
    }

    @Test
    fun `a Target that is not http or https is refused`() {
        for (bad in listOf("ftp://example.com", "javascript:alert(1)", "mailto:a@b.c", "not a url")) {
            create(bad).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { exists() }
            }
        }
        assertEquals(0, links.count())
    }

    @Test
    fun `an empty Target is refused`() {
        create("").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a Target pointing back at this service is refused`() {
        // Otherwise the Visitor bounces between the Interstitial and itself.
        create("https://to2.at/1").andExpect { status { isBadRequest() } }
        create("https://www.to2.at/1").andExpect { status { isBadRequest() } }

        assertEquals(0, links.count())
    }

    @Test
    fun `an anonymous request cannot create a Link`() {
        mvc.post("/links") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"target":"https://example.com"}"""
            }
            .andExpect { status { is3xxRedirection() } }

        assertEquals(0, links.count())
    }

    private fun codeOf(actions: org.springframework.test.web.servlet.ResultActionsDsl): Long {
        val body = actions.andReturn().response.contentAsString
        return Regex(""""code":"(\d+)"""").find(body)!!.groupValues[1].toLong()
    }
}
