package at.to2.back.link

import at.to2.back.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

class LinkNameTest : IntegrationTest() {
    @BeforeEach fun reset() = clearAll()

    private fun create(body: String) = mvc.post("/links") {
        with(asOwner("name-owner"))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun update(code: String, body: String, sub: String = "name-owner") = mvc.patch("/links/$code") {
        with(asOwner(sub))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun codeOf(body: String) = Regex(""""code":"(\d+)"""").find(body)!!.groupValues[1]

    @Test
    fun `an Owner creates a named Link and remembers it in the list`() {
        create("""{"target":"https://example.com/java","name":"  자바 메모리 수업  "}""").andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("자바 메모리 수업") }
            header { string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")) }
        }
        mvc.get("/links") { with(asOwner("name-owner")) }.andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("자바 메모리 수업") }
            jsonPath("$[0].target") { value("https://example.com/java") }
        }
    }

    @Test
    fun `Name and Target can change independently together and be cleared without changing identity`() {
        val created = create("""{"target":"https://example.com/original","name":"첫 이름"}""")
            .andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val code = codeOf(created)
        val createdAt = Regex(""""createdAt":"([^"]+)"""").find(created)!!.groupValues[1]
        update(code, """{"name":"  기억할 이름  "}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("기억할 이름") }
            jsonPath("$.target") { value("https://example.com/original") }
            jsonPath("$.shortUrl") { value("https://to2.at/$code") }
            jsonPath("$.createdAt") { value(createdAt) }
        }
        update(code, """{"target":"https://example.com/new"}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("기억할 이름") }
        }
        update(code, """{"target":"https://example.com/both","name":"함께 변경"}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("함께 변경") }
            jsonPath("$.target") { value("https://example.com/both") }
        }
        for (empty in listOf("\"\"", "\"   \"", "null")) {
            update(code, """{"name":$empty}""").andExpect { status { isOk() } }
                .andReturn().response.contentAsString.let { kotlin.test.assertTrue(it.contains("\"name\":null")) }
            update(code, """{"name":"다시 기억"}""").andExpect { status { isOk() } }
        }
        mvc.get("/to/$code").andExpect {
            jsonPath("$.target") { value("https://example.com/both") }
            jsonPath("$.name") { doesNotExist() }
        }
    }

    @Test
    fun `omitted empty and null create Names remain explicitly unnamed`() {
        for (name in listOf("", ",\"name\":null", ",\"name\":\"  \"")) {
            val body = create("""{"target":"https://example.com/unnamed"$name}""")
                .andExpect { status { isCreated() } }.andReturn().response.contentAsString
            kotlin.test.assertTrue(body.contains("\"name\":null"))
        }
        val body = mvc.get("/links") { with(asOwner("name-owner")) }.andReturn().response.contentAsString
        kotlin.test.assertEquals(3, Regex(""""name":null""").findAll(body).count())
    }

    @Test
    fun `a Name may have one hundred Unicode code points after trimming`() {
        val name = "😀".repeat(100)
        create("""{"target":"https://example.com/boundary","name":" $name "}""").andExpect {
            status { isCreated() }
            jsonPath("$.name") { value(name) }
        }
        create("""{"target":"https://example.com/too-long","name":"${"가".repeat(101)}"}""")
            .andExpect { status { isBadRequest() }; jsonPath("$.message") { exists() } }
        mvc.get("/links") { with(asOwner("name-owner")) }.andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun `invalid details reject the entire update and preserve the old Target and Name`() {
        val code = codeOf(create("""{"target":"https://example.com/original","name":"기존 이름"}""")
            .andReturn().response.contentAsString)
        for (name in listOf("123", "true", "[]", "{}", "\"${"a".repeat(101)}\"")) {
            create("""{"target":"https://example.com/invalid","name":$name}""")
                .andExpect { status { isBadRequest() }; jsonPath("$.message") { exists() } }
            update(code, """{"target":"https://example.com/changed","name":$name}""")
                .andExpect { status { isBadRequest() }; jsonPath("$.message") { exists() } }
        }
        for (body in listOf("{}", """{"ignored":true}""", """{"target":null,"name":"덮지 않음"}""", """{"target":"ftp://example.com","name":"덮지 않음"}""")) {
            update(code, body).andExpect { status { isBadRequest() } }
        }
        mvc.get("/links") { with(asOwner("name-owner")) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].target") { value("https://example.com/original") }
            jsonPath("$[0].name") { value("기존 이름") }
        }
    }

    @Test
    fun `an API key renames its suspended Link but cannot rename deleted or another Owner Links`() {
        val owner = anOwner("name-owner")
        val suspended = aLink(owner = owner, state = LinkState.SUSPENDED)
        val deleted = aLink(owner = owner, state = LinkState.DELETED)
        val other = aLink(owner = anOwner("another-owner"))
        val keyBody = mvc.post("/api-keys") {
            with(asOwner("name-owner")); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"name-test"}"""
        }.andReturn().response.contentAsString
        val key = Regex(""""key":"([^"]+)"""").find(keyBody)!!.groupValues[1]
        mvc.patch("/links/${suspended.code}") {
            header("Authorization", "Bearer $key"); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"중지된 수업"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("중지된 수업") }
            jsonPath("$.state") { value("SUSPENDED") }
            jsonPath("$.target") { value(suspended.target) }
            header { string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")) }
        }
        for (code in listOf(deleted.code, other.code)) {
            update(code, """{"name":"권한 없음"}""").andExpect { status { isNotFound() } }
        }
        mvc.get("/to/${suspended.code}").andExpect {
            jsonPath("$.state") { value("SUSPENDED") }
            jsonPath("$.target") { doesNotExist() }
            jsonPath("$.name") { doesNotExist() }
        }
    }

    @Test
    fun `create replay returns the current Name without overwriting a rename`() {
        val request = """{"target":"https://example.com/replay","name":"최초 이름","requestId":"bfe4974a-b9fe-8c25-93ed-3d0e9c1398d1"}"""
        val code = codeOf(create(request).andExpect { status { isCreated() } }.andReturn().response.contentAsString)
        update(code, """{"name":"기억할 이름"}""").andExpect { status { isOk() } }
        create(request).andExpect {
            status { isOk() }
            jsonPath("$.code") { value(code) }
            jsonPath("$.name") { value("기억할 이름") }
        }
    }
}
