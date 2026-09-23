package at.to2.back.owner

import at.to2.back.support.IntegrationTest
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.get

/**
 * The deployment terminates TLS at the edge and forwards over plain HTTP.
 * Without honouring the forwarded headers, every absolute URL the framework
 * builds comes out as `http://` — including the OAuth `redirect_uri`, which then
 * fails to match the `https://` one registered with Kakao and breaks login.
 *
 * The failure is invisible locally, where everything really is http, so it is
 * pinned here instead.
 */
@TestPropertySource(properties = ["server.forward-headers-strategy=framework"])
class ForwardedProtoTest : IntegrationTest() {

    @Test
    fun `the Kakao redirect_uri is built as https when the edge forwarded https`() {
        val location =
            mvc.get("/oauth2/authorization/kakao") {
                    header("X-Forwarded-Proto", "https")
                    header("X-Forwarded-Host", "api.example.test")
                }
                .andReturn()
                .response
                .getHeader("Location")!!

        val redirectUri = redirectUriOf(location)

        assertTrue(
            redirectUri.startsWith("https://api.example.test/"),
            "redirect_uri must follow the forwarded scheme and host, was: $redirectUri",
        )
    }

    @Test
    fun `nothing forwarded means the request's own scheme is used`() {
        // The control: the scheme is taken from the request, not hard-coded to
        // https, so a plain local run still produces a usable redirect_uri.
        val location =
            mvc.get("/oauth2/authorization/kakao").andReturn().response.getHeader("Location")!!

        assertTrue(redirectUriOf(location).startsWith("http://localhost/"), location)
    }

    private fun redirectUriOf(location: String): String =
        Regex("redirect_uri=([^&]+)").find(location)!!.groupValues[1].let {
            java.net.URLDecoder.decode(it, Charsets.UTF_8)
        }
}
