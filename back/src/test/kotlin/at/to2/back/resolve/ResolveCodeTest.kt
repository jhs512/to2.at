package at.to2.back.resolve

import at.to2.back.link.LinkState
import at.to2.back.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

class ResolveCodeTest : IntegrationTest() {

    @BeforeEach fun reset() = clearAll()

    @Test
    fun `an active Code resolves to its Target`() {
        val link = aLink(target = "https://example.com/hello")

        mvc.get("/to/${link.code}").andExpect {
            status { isOk() }
            jsonPath("$.code") { value(link.code) }
            jsonPath("$.state") { value("ACTIVE") }
            jsonPath("$.target") { value("https://example.com/hello") }
        }
    }

    @Test
    fun `resolving needs no authentication`() {
        val link = aLink()

        // No session, no credentials: a Visitor must be able to open a short
        // address that someone shared with them.
        mvc.get("/to/${link.code}").andExpect { status { isOk() } }
    }

    @Test
    fun `an unknown Code is not found`() {
        mvc.get("/to/999999").andExpect { status { isNotFound() } }
    }

    @Test
    fun `a deleted Code reports its state without disclosing the Target`() {
        val link = aLink(target = "https://example.com/gone", state = LinkState.DELETED)

        mvc.get("/to/${link.code}").andExpect {
            status { isOk() }
            jsonPath("$.state") { value("DELETED") }
            jsonPath("$.target") { doesNotExist() }
        }
    }

    @Test
    fun `a suspended Code reports its state without disclosing the Target`() {
        val link = aLink(target = "https://example.com/bad", state = LinkState.SUSPENDED)

        mvc.get("/to/${link.code}").andExpect {
            status { isOk() }
            jsonPath("$.state") { value("SUSPENDED") }
            jsonPath("$.target") { doesNotExist() }
        }
    }

    @Test
    fun `a non-canonical spelling of a Code is not found`() {
        val link = aLink()

        // One Link, one address. `/01` and `/0000001` must not be aliases for
        // `/1`, or the same Link becomes reachable at unboundedly many URLs.
        mvc.get("/to/0${link.code}").andExpect { status { isNotFound() } }
        mvc.get("/to/abc").andExpect { status { isNotFound() } }
        mvc.get("/to/0").andExpect { status { isNotFound() } }
    }
}
