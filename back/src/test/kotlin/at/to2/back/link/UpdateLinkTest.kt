package at.to2.back.link

import at.to2.back.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.options
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UpdateLinkTest : IntegrationTest() {
    @BeforeEach fun reset() = clearAll()

    private fun keyFor(sub: String): String {
        val body = mvc.post("/api-keys") {
            with(asOwner(sub))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"target-edit-test"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return Regex(""""key":"([^"]+)"""").find(body)!!.groupValues[1]
    }

    @Test
    fun `an API key changes its Owner Target while preserving the Code and short address`() {
        val owner = anOwner("target-owner")
        val link = aLink(owner = owner)
        val key = keyFor("target-owner")
        val before = mvc.get("/links") { header("Authorization", "Bearer $key") }
            .andReturn().response.contentAsString
        val createdAt = Regex(""""createdAt":"([^"]+)"""").find(before)!!.groupValues[1]
        mvc.patch("/links/${link.code}") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"https://example.com/updated"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value(link.code) }
            jsonPath("$.shortUrl") { value("https://to2.at/${link.code}") }
            jsonPath("$.target") { value("https://example.com/updated") }
            jsonPath("$.state") { value("ACTIVE") }
            jsonPath("$.createdAt") { value(createdAt) }
        }
        mvc.get("/to/${link.code}").andExpect {
            status { isOk() }
            jsonPath("$.target") { value("https://example.com/updated") }
        }
        mvc.get("/links") { header("Authorization", "Bearer $key") }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].code") { value(link.code) }
        }
    }

    @Test
    fun `a Kakao Owner can update the Target without changing the Link identity`() {
        val link = aLink(owner = anOwner("session-owner"))
        mvc.patch("/links/${link.code}") {
            with(asOwner("session-owner"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":" https://example.com/session-updated "}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value(link.code) }
            jsonPath("$.target") { value("https://example.com/session-updated") }
        }
    }

    @Test
    fun `updating a suspended Link preserves Suspension and withholds the public Target`() {
        val link = aLink(owner = anOwner("suspended-owner"), state = LinkState.SUSPENDED)
        val stateChangedAt = links.findById(link.id!!).orElseThrow().stateChangedAt
        mvc.patch("/links/${link.code}") {
            header("Authorization", "Bearer ${keyFor("suspended-owner")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"https://example.com/new-suspended"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.state") { value("SUSPENDED") }
            jsonPath("$.target") { value("https://example.com/new-suspended") }
        }
        mvc.get("/to/${link.code}").andExpect {
            jsonPath("$.state") { value("SUSPENDED") }
            jsonPath("$.target") { doesNotExist() }
        }
        assertEquals(stateChangedAt, links.findById(link.id!!).orElseThrow().stateChangedAt)
    }

    @Test
    fun `deleted missing and another Owner Links cannot be updated`() {
        val own = anOwner("edit-owner")
        val deleted = aLink(owner = own, state = LinkState.DELETED)
        val other = aLink(owner = anOwner("other-owner"))
        val key = keyFor("edit-owner")
        for (code in listOf(deleted.code, other.code, "9223372036854775807", "0", "01", "invalid")) {
            mvc.patch("/links/$code") {
                header("Authorization", "Bearer $key")
                contentType = MediaType.APPLICATION_JSON
                content = """{"target":"https://example.com/forbidden"}"""
            }.andExpect { status { isNotFound() } }
        }
        mvc.get("/to/${other.code}").andExpect {
            jsonPath("$.target") { value(other.target) }
        }
        mvc.get("/to/${deleted.code}").andExpect {
            jsonPath("$.state") { value("DELETED") }
            jsonPath("$.target") { doesNotExist() }
        }
    }

    @Test
    fun `invalid Targets leave the existing Link unchanged`() {
        val link = aLink(owner = anOwner("validate-owner"))
        val key = keyFor("validate-owner")
        for (target in listOf("", "not-a-url", "javascript:alert(1)", "https://to2.at/1", "https://api.to2.at/")) {
            mvc.patch("/links/${link.code}") {
                header("Authorization", "Bearer $key")
                contentType = MediaType.APPLICATION_JSON
                content = """{"target":"$target"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { exists() }
            }
        }
        mvc.patch("/links/${link.code}") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isBadRequest() } }
        mvc.get("/to/${link.code}").andExpect { jsonPath("$.target") { value(link.target) } }
    }

    @Test
    fun `anonymous invalid and revoked credentials cannot update a Target`() {
        val link = aLink(owner = anOwner("auth-owner"))
        val key = keyFor("auth-owner")
        val metadata = mvc.get("/api-keys") { with(asOwner("auth-owner")) }
            .andReturn().response.contentAsString
        val id = Regex(""""id":(\d+)""").find(metadata)!!.groupValues[1]
        mvc.delete("/api-keys/$id") { with(asOwner("auth-owner")) }
            .andExpect { status { isNoContent() } }
        for (credential in listOf("invalid", key)) {
            val body = mvc.patch("/links/${link.code}") {
                header("Authorization", "Bearer $credential")
                contentType = MediaType.APPLICATION_JSON
                content = """{"target":"https://example.com/blocked"}"""
            }.andExpect {
                status { isUnauthorized() }
                header { doesNotExist("Location") }
            }.andReturn().response.contentAsString
            assertFalse(body.contains(credential))
        }
        mvc.patch("/links/${link.code}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"https://example.com/anonymous"}"""
        }.andExpect { status { is3xxRedirection() } }
        mvc.get("/to/${link.code}").andExpect { jsonPath("$.target") { value(link.target) } }
    }

    @Test
    fun `the configured site may preflight a Target update`() {
        mvc.options("/links/1") {
            header("Origin", "http://localhost:5173")
            header("Access-Control-Request-Method", "PATCH")
            header("Access-Control-Request-Headers", "content-type")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", "http://localhost:5173") }
            header { string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")) }
        }
    }

    @Test
    fun `a saved Target is immediately resolved even with old conditional request headers`() {
        val link = aLink(owner = anOwner("cache-owner"))
        val noStore = org.hamcrest.Matchers.containsString("no-store")
        mvc.get("/to/${link.code}").andExpect {
            status { isOk() }
            header { string("Cache-Control", noStore) }
            jsonPath("$.target") { value(link.target) }
        }
        mvc.patch("/links/${link.code}") {
            with(asOwner("cache-owner"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"https://example.com/after-save#new"}"""
        }.andExpect { status { isOk() }; header { string("Cache-Control", noStore) } }
        mvc.get("/to/${link.code}") {
            header("If-None-Match", "\"old-target\"")
            header("If-Modified-Since", "Wed, 21 Oct 2099 07:28:00 GMT")
        }.andExpect {
            status { isOk() }
            header { string("Cache-Control", noStore) }
            header { string("Expires", "0") }
            jsonPath("$.target") { value("https://example.com/after-save#new") }
        }
        mvc.get("/links") { with(asOwner("cache-owner")) }.andExpect {
            header { string("Cache-Control", noStore) }
            jsonPath("$[0].target") { value("https://example.com/after-save#new") }
        }
    }
}
