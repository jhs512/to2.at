package at.to2.back.link

import at.to2.back.support.IntegrationTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class ManageLinksTest : IntegrationTest() {

    @BeforeEach fun reset() = clearAll()

    private fun create(sub: String, target: String): String {
        val body =
            mvc.post("/links") {
                    with(asOwner(sub))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"target":"$target"}"""
                }
                .andReturn()
                .response
                .contentAsString
        return Regex(""""code":"(\d+)"""").find(body)!!.groupValues[1]
    }

    @Test
    fun `an Owner sees their own Links, newest first`() {
        create("kakao-1", "https://example.com/first")
        create("kakao-1", "https://example.com/second")

        mvc.get("/links") { with(asOwner("kakao-1")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].target") { value("https://example.com/second") }
                jsonPath("$[1].target") { value("https://example.com/first") }
                jsonPath("$[0].code") { exists() }
                jsonPath("$[0].createdAt") { exists() }
            }
    }

    @Test
    fun `an Owner never sees another Owner's Links`() {
        create("kakao-1", "https://example.com/mine")

        mvc.get("/links") { with(asOwner("kakao-2")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `deleting a Link stops it resolving`() {
        val code = create("kakao-1", "https://example.com/gone")

        mvc.delete("/links/$code") { with(asOwner("kakao-1")) }.andExpect { status { isNoContent() } }

        mvc.get("/to/$code").andExpect {
            status { isOk() }
            jsonPath("$.state") { value("DELETED") }
            jsonPath("$.target") { doesNotExist() }
        }
    }

    @Test
    fun `a deleted Link leaves the list`() {
        val code = create("kakao-1", "https://example.com/gone")
        create("kakao-1", "https://example.com/kept")

        mvc.delete("/links/$code") { with(asOwner("kakao-1")) }

        mvc.get("/links") { with(asOwner("kakao-1")) }
            .andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].target") { value("https://example.com/kept") }
            }
    }

    @Test
    fun `a deleted Code is never reissued`() {
        val deleted = create("kakao-1", "https://example.com/gone")
        mvc.delete("/links/$deleted") { with(asOwner("kakao-1")) }

        val next = create("kakao-1", "https://example.com/new")

        // The row survives deletion precisely so this can never collide: an
        // address already shared must not start pointing somewhere else.
        assertEquals(deleted.toLong() + 1, next.toLong())
        assertEquals(2, links.count())
    }

    @Test
    fun `another Owner cannot delete my Link, and is not told it exists`() {
        val code = create("kakao-1", "https://example.com/mine")

        mvc.delete("/links/$code") { with(asOwner("kakao-2")) }
            // Not-found rather than forbidden: a refusal that confirms existence
            // still leaks which Codes are taken.
            .andExpect { status { isNotFound() } }

        mvc.get("/to/$code").andExpect { jsonPath("$.state") { value("ACTIVE") } }
    }

    @Test
    fun `deleting twice is not found the second time`() {
        val code = create("kakao-1", "https://example.com/x")

        mvc.delete("/links/$code") { with(asOwner("kakao-1")) }.andExpect { status { isNoContent() } }
        mvc.delete("/links/$code") { with(asOwner("kakao-1")) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `an anonymous request can neither list nor delete`() {
        val code = create("kakao-1", "https://example.com/x")

        mvc.get("/links").andExpect { status { is3xxRedirection() } }
        mvc.delete("/links/$code").andExpect { status { is3xxRedirection() } }

        mvc.get("/to/$code").andExpect { jsonPath("$.state") { value("ACTIVE") } }
    }
}
