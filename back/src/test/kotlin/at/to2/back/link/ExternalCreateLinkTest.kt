package at.to2.back.link

import at.to2.back.support.IntegrationTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.delete
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExternalCreateLinkTest : IntegrationTest() {
    @BeforeEach fun reset() = clearAll()

    @Test
    fun `replaying an external create request returns the same Link`() {
        val request = """{"target":"https://java-viz.oa.gg/web/app/#content=abc%2Bdef","requestId":"bfe4974a-b9fe-8c25-93ed-3d0e9c1398d1"}"""
        val first = mvc.post("/links") {
            with(asOwner("external-owner"))
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val second = mvc.post("/links") {
            with(asOwner("external-owner"))
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertEquals(first, second)
    }

    private val id = "bfe4974a-b9fe-8c25-93ed-3d0e9c1398d1"
    private val target = "https://java-viz.oa.gg/web/app/#content=abc%2Bdef"

    private fun create(sub: String = "external-owner", target: String = this.target, requestId: String = id) = mvc.post("/links") {
        with(asOwner(sub))
        contentType = MediaType.APPLICATION_JSON
        content = """{"target":"$target","requestId":"$requestId"}"""
    }

    private fun codeOf(body: String) = Regex(""""code":"(\d+)"""").find(body)!!.groupValues[1]

    @Test
    fun `request identity belongs to the Owner not the browser`() {
        val first = codeOf(create().andExpect { status { isCreated() } }.andReturn().response.contentAsString)
        val second = codeOf(create(sub = "another-owner").andExpect { status { isCreated() } }.andReturn().response.contentAsString)
        kotlin.test.assertNotEquals(first, second)
        create().andExpect { status { isOk() }; jsonPath("$.code") { value(first) } }
    }

    @Test
    fun `different Target cannot reuse the request identity`() {
        create().andExpect { status { isCreated() } }
        create(target = "https://example.com/changed").andExpect { status { isConflict() } }
        mvc.get("/links") { with(asOwner("external-owner")) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].target") { value(target) }
        }
    }

    @Test
    fun `a Target edit makes replay conflict rather than silently sharing a different Target`() {
        val code = codeOf(create().andReturn().response.contentAsString)
        mvc.patch("/links/$code") {
            with(asOwner("external-owner"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"target":"https://example.com/edited"}"""
        }.andExpect { status { isOk() } }
        create().andExpect { status { isConflict() } }
    }

    @Test
    fun `a deleted or suspended result never creates a replacement Link`() {
        val code = codeOf(create().andReturn().response.contentAsString)
        mvc.delete("/links/$code") { with(asOwner("external-owner")) }.andExpect { status { isNoContent() } }
        create().andExpect { status { isOk() }; jsonPath("$.state") { value("DELETED") }; jsonPath("$.code") { value(code) } }
        val suspended = aLink(owner = anOwner("suspended-owner"), target = target, state = LinkState.SUSPENDED)
        jdbc.update("UPDATE link SET create_request_id = ?::uuid WHERE id = ?", id, suspended.id)
        create(sub = "suspended-owner").andExpect { status { isOk() }; jsonPath("$.state") { value("SUSPENDED") }; jsonPath("$.code") { value(suspended.code) } }
        assertEquals(2, links.count())
    }

    @Test
    fun `a 128 KiB share Target preserves its entire fragment through JSON PostgreSQL and resolution`() {
        val longTarget = "https://java-viz.oa.gg/web/app/#payload=" + "ABCdef_012-".repeat(14000) + "%2B%26%3D&mode=java"
        val code = codeOf(create(target = longTarget).andExpect {
            status { isCreated() }
            jsonPath("$.target") { value(longTarget) }
        }.andReturn().response.contentAsString)
        create(target = longTarget).andExpect { status { isOk() }; jsonPath("$.code") { value(code) }; jsonPath("$.target") { value(longTarget) } }
        mvc.get("/to/$code").andExpect { status { isOk() }; jsonPath("$.target") { value(longTarget) } }
    }

    @Test
    fun `concurrent external requests allocate exactly one Code`() {
        anOwner("concurrent-owner")
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                executor.submit<String> {
                    barrier.await(10, TimeUnit.SECONDS)
                    val response = create(sub = "concurrent-owner").andReturn().response
                    kotlin.test.assertTrue(response.status in listOf(200, 201), "status=${response.status}")
                    codeOf(response.contentAsString)
                }
            }
            val codes = futures.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(codes[0], codes[1])
            mvc.get("/links") { with(asOwner("concurrent-owner")) }.andExpect { jsonPath("$.length()") { value(1) } }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `invalid request identity is rejected before a Link is created`() {
        create(requestId = "not-a-uuid").andExpect { status { isBadRequest() } }
        assertEquals(0, links.count())
    }
}
