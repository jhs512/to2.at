package at.to2.back.admin

import at.to2.back.owner.OwnerRepository
import at.to2.back.support.IntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class DevelopmentSeedTest {

    @Nested
    @ActiveProfiles("dev")
    inner class UnderTheDevelopmentProfile : IntegrationTest() {

        @Autowired private lateinit var accounts: LocalAccountRepository
        @Autowired private lateinit var devOwners: OwnerRepository

        @Test
        fun `sample Owners exist and can be signed in as without Kakao`() {
            listOf("user1", "user2", "user3").forEach { username ->
                val account = accounts.findByUsername(username)
                assertNotNull(account, "$username should be seeded")
                assertNotNull(account.ownerId, "$username should own an Owner row")
                assertTrue(account.passwordHash.startsWith("{"), "hash carries its algorithm")
            }
        }

        @Test
        fun `seeded Owners keep the Kakao subject column populated`() {
            // Synthetic rather than null, so the "one Kakao account = one Owner"
            // invariant stays structural and cannot collide with a real subject.
            val owner = devOwners.findByKakaoSub("dev:user1")
            assertNotNull(owner)
            assertEquals("user1", owner.nickname)
        }

        @Test
        fun `a seeded account can actually drive the Owner flows without Kakao`() {
            // The point of these accounts. Creating them but leaving the Owner
            // endpoints reachable only by a Kakao principal would make them
            // decorative.
            val user1 = org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.user(
                    org.springframework.security.core.userdetails.User.withUsername("user1")
                        .password("x")
                        .authorities(emptyList())
                        .build()
                )

            mvc.post("/links") {
                    with(user1)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"target":"https://example.com/dev"}"""
                }
                .andExpect { status { isCreated() } }

            mvc.get("/links") { with(user1) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$[0].target") { value("https://example.com/dev") }
                }
        }

        @Test
        fun `the Operator cannot create Links even though it can sign in`() {
            val operator = org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.user(
                    org.springframework.security.core.userdetails.User.withUsername("admin")
                        .password("x")
                        .roles(ROLE_OPERATOR)
                        .build()
                )

            // Administration and ownership stay separate: the Operator account
            // has no Owner row and so nothing to own Links with.
            mvc.post("/links") {
                    with(operator)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"target":"https://example.com/x"}"""
                }
                .andExpect { status { isForbidden() } }
        }

        @Test
        fun `this seed creates sample users and nothing else`() {
            // The Operator comes from the environment, not from here. If this
            // seed ever grew an admin account it would be a way into production
            // that no environment variable gates.
            assertNull(accounts.findByUsername("admin"))
            assertEquals(3, accounts.count())
            assertTrue(accounts.findAll().all { it.ownerId != null && !it.operator })
        }
    }

    @Nested
    inner class WithoutTheDevelopmentProfile : IntegrationTest() {

        @Autowired private lateinit var accounts: LocalAccountRepository

        @Test
        fun `no sample account can come into existence`() {
            listOf("user1", "user2", "user3").forEach {
                assertNull(accounts.findByUsername(it), "$it must not exist outside development")
            }
        }
    }
}
