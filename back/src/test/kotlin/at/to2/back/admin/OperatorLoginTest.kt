package at.to2.back.admin

import at.to2.back.support.IntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@TestPropertySource(properties = ["to2.admin.password=", "to2.admin.max-failed-attempts=3"])
class OperatorLoginTest : IntegrationTest() {

    @Autowired private lateinit var accounts: LocalAccountRepository
    @Autowired private lateinit var encoder: PasswordEncoder

    @BeforeEach
    fun reset() {
        accounts.deleteAll()
        clearAll()
        accounts.save(
            LocalAccount(
                username = "admin",
                passwordHash = encoder.encode("correct horse")!!,
                operator = true,
            )
        )
    }

    private fun login(password: String, username: String = "admin") =
        mvc.post("/admin/login") {
            param("username", username)
            param("password", password)
            with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        }

    @Test
    fun `the Operator signs in with a username and password`() {
        login("correct horse").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin")
        }
    }

    @Test
    fun `a wrong password is refused`() {
        login("wrong").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/login?error")
        }
    }

    @Test
    fun `the admin path is not linked from anywhere public and needs the Operator role`() {
        mvc.get("/admin").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/login")
        }
    }

    @Test
    fun `the rendered login form can actually be submitted`() {
        // Driving the real form rather than a synthetic request with a token
        // injected. A hand-written form that forgets its CSRF input looks
        // perfectly fine to a test that supplies one, and is broken for every
        // real browser.
        //
        // The session lives in the database and travels by cookie, so the
        // cookie is what has to be carried between the two requests — the
        // servlet's own session object is empty here.
        val page = mvc.get("/admin/login").andReturn()
        val html = page.response.contentAsString
        val token =
            Regex("""name="_csrf" value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: error("the login form renders no CSRF input, so no browser can submit it")
        val sessionCookie =
            page.response.getCookie("SESSION") ?: error("no session cookie was issued")

        mvc.post("/admin/login") {
                cookie(sessionCookie)
                param("username", "admin")
                param("password", "correct horse")
                param("_csrf", token)
            }
            .andExpect { redirectedUrl("/admin") }
    }

    @Test
    fun `the login page tells crawlers to stay away`() {
        mvc.get("/admin/login").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("noindex")) }
        }
    }

    @Test
    fun `the two chains do not interfere`() {
        // This is the whole reason there are two (ADR 0005). On one chain, with
        // Kakao as the only registered client, the entry point redirects
        // straight to the provider and the password form becomes unreachable.
        mvc.get("/me").andExpect { redirectedUrl("/oauth2/authorization/kakao") }
        mvc.get("/admin").andExpect { redirectedUrl("/admin/login") }
    }

    @Test
    fun `repeated failures lock the account, and it refuses the right password too`() {
        repeat(3) { login("wrong") }

        val locked = accounts.findByUsername("admin")!!
        assertNotNull(locked.lockedUntil)
        assertTrue(locked.isLocked())

        // The lock has to bite for the correct password as well, or it is not a
        // lock — just a speed bump for the guesser.
        login("correct horse").andExpect { redirectedUrl("/admin/login?error") }
    }

    @Test
    fun `the lock releases once its window has passed`() {
        repeat(3) { login("wrong") }
        login("correct horse").andExpect { redirectedUrl("/admin/login?error") }

        // Move the clock past the window rather than waiting it out. A lock that
        // never released would be indistinguishable from a broken account.
        val account = accounts.findByUsername("admin")!!
        account.lockedUntil = java.time.Instant.now().minusSeconds(1)
        accounts.save(account)

        login("correct horse").andExpect { redirectedUrl("/admin") }
    }

    @Test
    fun `a seeded user can sign in and is not dropped onto the console`() {
        // The Operator console is not theirs, and landing a signed-in user on a
        // 403 would make the development accounts useless for Owner flows.
        accounts.save(
            LocalAccount(
                username = "user1",
                passwordHash = encoder.encode("user1")!!,
                operator = false,
                ownerId = anOwner(kakaoSub = "dev:user1").id,
            )
        )

        login("user1", username = "user1").andExpect { redirectedUrl("/me") }
    }

    @Test
    fun `a successful login clears the failure count`() {
        login("wrong")
        login("wrong")
        assertEquals(2, accounts.findByUsername("admin")!!.failedAttempts)

        login("correct horse").andExpect { redirectedUrl("/admin") }

        assertEquals(0, accounts.findByUsername("admin")!!.failedAttempts)
    }

    @Test
    fun `the Operator is not an Owner and so has no Links`() {
        // Administration and ownership stay separate: the Operator account has
        // no Owner row and nothing on the public chain accepts its session.
        assertEquals(null, accounts.findByUsername("admin")!!.ownerId)
        assertEquals(0, owners.count())
    }
}
