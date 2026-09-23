package at.to2.back.owner

import at.to2.back.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KakaoLoginTest : IntegrationTest() {

    @BeforeEach fun reset() = clearAll()

    @Test
    fun `an anonymous request is sent straight to Kakao`() {
        // Kakao is the only registered client, so there is no login page to
        // stop at. A Visitor who needs to sign in goes to Kakao in one hop.
        mvc.get("/me").andExpect {
            status { is3xxRedirection() }
            header { string("Location", "/oauth2/authorization/kakao") }
        }
    }

    @Test
    fun `first login creates the Owner, with no separate sign-up step`() {
        mvc.get("/me") { with(kakao(sub = "kakao-1", nickname = "장금이")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.nickname") { value("장금이") }
            }

        assertEquals(1, owners.count())
    }

    @Test
    fun `logging in again reuses the same Owner`() {
        repeat(3) { mvc.get("/me") { with(kakao(sub = "kakao-1")) }.andExpect { status { isOk() } } }

        // The Kakao subject is the identity; repeat logins must not accumulate
        // rows or the Owner's Links would be orphaned from their creator.
        assertEquals(1, owners.count())
    }

    @Test
    fun `a changed Kakao nickname is picked up on the next login`() {
        mvc.get("/me") { with(kakao(sub = "kakao-1", nickname = "옛이름")) }
        mvc.get("/me") { with(kakao(sub = "kakao-1", nickname = "새이름")) }
            .andExpect { jsonPath("$.nickname") { value("새이름") } }

        assertEquals(1, owners.count())
    }

    @Test
    fun `an unverified email is not handed out`() {
        mvc.get("/me") { with(kakao(sub = "kakao-1", email = "a@b.c", emailVerified = false)) }
            .andExpect { jsonPath("$.email") { doesNotExist() } }

        // Stored as profile data, but withheld: Kakao supplies email only on a
        // Biz app and even then it may be unverified, so the service must not
        // hand it out as though it vouched for it.
        assertEquals("a@b.c", owners.findByKakaoSub("kakao-1")!!.email)
        assertFalse(owners.findByKakaoSub("kakao-1")!!.emailVerified)
    }

    @Test
    fun `a verified email is handed out`() {
        mvc.get("/me") { with(kakao(sub = "kakao-1", email = "a@b.c", emailVerified = true)) }
            .andExpect { jsonPath("$.email") { value("a@b.c") } }
    }

    @Test
    fun `Owners with no email are fine`() {
        mvc.get("/me") { with(kakao(sub = "kakao-1", email = null)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.email") { doesNotExist() }
            }
    }

    @Test
    fun `the session cookie that must survive the Kakao round trip is SameSite=Lax`() {
        // Starting the authorization flow is what creates the session holding
        // the pending authorization request — the exact cookie that has to come
        // back with Kakao's callback.
        val result = mvc.get("/oauth2/authorization/kakao").andReturn()

        val setCookie = result.response.getHeaders("Set-Cookie").joinToString("; ")
        assertAll(
            // Strict would withhold the cookie on Kakao's callback navigation
            // and break login entirely.
            { assertTrue(setCookie.contains("SameSite=Lax", ignoreCase = true), setCookie) },
            { assertTrue(setCookie.contains("HttpOnly", ignoreCase = true), setCookie) },
        )
    }

    @Test
    fun `resolving a Code still needs no session`() {
        val link = aLink()

        mvc.get("/to/${link.code}").andExpect { status { isOk() } }
    }

    private fun kakao(
        sub: String,
        nickname: String = "테스터",
        email: String? = null,
        emailVerified: Boolean = false,
    ) =
        oidcLogin().idToken {
            it.subject(sub)
            it.claim("nickname", nickname)
            email?.let { value -> it.claim("email", value) }
            it.claim("email_verified", emailVerified)
        }
}
