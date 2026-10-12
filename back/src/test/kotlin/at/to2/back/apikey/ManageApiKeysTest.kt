package at.to2.back.apikey

import at.to2.back.support.IntegrationTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.delete

@ExtendWith(OutputCaptureExtension::class)
class ManageApiKeysTest : IntegrationTest() {
    @BeforeEach fun reset() = clearAll()

    private fun issue(sub: String): Pair<Long, String> {
        val body = mvc.post("/api-keys") {
            with(asOwner(sub))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"tool"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return Regex(""""id":(\d+)""").find(body)!!.groupValues[1].toLong() to
            Regex(""""key":"([^"]+)"""").find(body)!!.groupValues[1]
    }

    @Test
    fun `an API key manages only its Owner Links and is rejected after revocation`() {
        val (id, key) = issue("key-owner")
        val other = aLink(owner = anOwner("other-owner"))
        val body = mvc.post("/links") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"https://example.com/mine"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val code = Regex(""""code":"(\d+)"""").find(body)!!.groupValues[1]
        mvc.get("/links") { header("Authorization", "Bearer $key") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].code") { value(code) }
        }
        mvc.delete("/links/${other.code}") { header("Authorization", "Bearer $key") }
            .andExpect { status { isNotFound() } }
        mvc.delete("/links/$code") { header("Authorization", "Bearer $key") }
            .andExpect { status { isNoContent() } }
        mvc.delete("/api-keys/$id") { with(asOwner("key-owner")) }
            .andExpect { status { isNoContent() } }
        mvc.get("/links") { header("Authorization", "Bearer $key") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a Kakao Owner receives a key once and lists only its metadata`(output: CapturedOutput) {
        val response = mvc.post("/api-keys") {
            with(asOwner("key-owner"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Codex"}"""
        }.andExpect {
            status { isCreated() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.name") { value("Codex") }
            jsonPath("$.key") { exists() }
        }.andReturn().response.contentAsString
        val key = Regex(""""key":"([^"]+)"""").find(response)!!.groupValues[1]
        assertTrue(key.startsWith("to2_"))
        val list = mvc.get("/api-keys") { with(asOwner("key-owner")) }.andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Codex") }
            jsonPath("$[0].key") { doesNotExist() }
            jsonPath("$[0].keyHash") { doesNotExist() }
        }.andReturn().response.contentAsString
        assertFalse(list.contains(key))
        // Only a verification hash, never the credential, survives issuance.
        val stored = jdbc.queryForObject("select key_hash from api_key", String::class.java)!!
        assertFalse(stored.contains(key))
        assertTrue(stored.matches(Regex("[0-9a-f]{64}")))
        assertFalse(output.all.contains(key))
    }

    @Test
    fun `a key cannot manage keys or become a browser session`() {
        val (_, key) = issue("key-owner")
        mvc.get("/api-keys") { header("Authorization", "Bearer $key") }
            .andExpect { status { isForbidden() } }
        mvc.post("/api-keys") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"escalation"}"""
        }.andExpect { status { isForbidden() } }
        mvc.get("/me") { header("Authorization", "Bearer $key") }.andExpect {
            status { isOk() }
            header { doesNotExist("Set-Cookie") }
        }
        mvc.get("/links").andExpect { status { is3xxRedirection() } }
    }

    @Test
    fun `invalid keys are rejected without reflecting credentials or falling back to Kakao`() {
        for (authorization in listOf("Bearer to2_" + "x".repeat(43), "Bearer invalid", "Basic invalid")) {
            val response = mvc.get("/links") {
                with(asOwner("browser-owner"))
                header("Authorization", authorization)
            }.andExpect {
                status { isUnauthorized() }
                header { doesNotExist("Location") }
                jsonPath("$.message") { value("유효한 API 키가 필요합니다.") }
                header { string("Content-Type", "application/json;charset=UTF-8") }
            }.andReturn().response.contentAsString
            assertFalse(response.contains(authorization))
        }
    }

    @Test
    fun `another Owner cannot list or revoke keys and a revoked key stays revoked`() {
        val (id, key) = issue("key-owner")
        mvc.get("/api-keys") { with(asOwner("other-owner")) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        mvc.delete("/api-keys/$id") { with(asOwner("other-owner")) }
            .andExpect { status { isNotFound() } }
        mvc.get("/me") { header("Authorization", "Bearer $key") }
            .andExpect { status { isOk() } }
        repeat(2) {
            mvc.delete("/api-keys/$id") { with(asOwner("key-owner")) }
                .andExpect { status { isNoContent() } }
        }
        mvc.get("/api-keys") { with(asOwner("key-owner")) }.andExpect {
            jsonPath("$[0].revokedAt") { exists() }
            jsonPath("$[0].key") { doesNotExist() }
        }
        mvc.post("/links") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"https://example.com/revoked"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `key names are validated and anonymous Visitors cannot issue keys`() {
        for (name in listOf("", " ".repeat(2), "x".repeat(81))) {
            mvc.post("/api-keys") {
                with(asOwner("key-owner"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"$name"}"""
            }.andExpect { status { isBadRequest() } }
        }
        mvc.post("/api-keys") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"anonymous"}"""
        }.andExpect { status { is3xxRedirection() } }
        assertTrue(jdbc.queryForObject("select count(*) from api_key", Long::class.java) == 0L)
    }
}
