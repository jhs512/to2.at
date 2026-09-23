package at.to2.back.admin

import at.to2.back.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@TestPropertySource(properties = ["to2.admin.password="])
class AdminAccessTest : IntegrationTest() {

    @BeforeEach fun reset() = clearAll()

    @Test
    fun `an anonymous request cannot reach the console`() {
        mvc.get("/admin").andExpect { redirectedUrl("/admin/login") }
    }

    @Test
    fun `an Owner session is not an Operator session`() {
        // A signed-in Owner is still just a member of the public. Holding a
        // Kakao session must not carry any power over other people's Links.
        mvc.get("/admin") { with(asOwner("kakao-1")) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `an account with no Owner is refused everywhere, not just where a handler happens to sit`() {
        val operator = org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.user(
                org.springframework.security.core.userdetails.User.withUsername("nobody")
                    .password("x")
                    .roles(ROLE_OPERATOR)
                    .build()
            )

        // /links refuses it because LinkController carries an exception handler.
        // /me went through the same check with no handler in scope and returned
        // a 500 — the same refusal has to look the same wherever it happens.
        mvc.get("/me") { with(operator) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an Owner cannot suspend a Link`() {
        val link = aLink()

        mvc.post("/admin/links/${link.code}/suspend") {
                with(asOwner("kakao-1"))
                with(csrf())
            }
            .andExpect { status { isForbidden() } }

        mvc.get("/to/${link.code}").andExpect { jsonPath("$.state") { value("ACTIVE") } }
    }
}
