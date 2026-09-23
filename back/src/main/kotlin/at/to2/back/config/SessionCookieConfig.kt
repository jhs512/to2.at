package at.to2.back.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "to2.session-cookie")
data class SessionCookieProperties(
    /** Off locally so plain http works; the deployment turns it on. */
    val secure: Boolean = false
)

@Configuration
class SessionCookieConfig {

    /**
     * Set here rather than through `server.servlet.session.cookie.*` because
     * that path only applies when an embedded web server is present — it would
     * silently not apply in tests, and this is not a setting we want unverified.
     *
     * `SameSite=Lax` is load-bearing: Kakao's callback is a cross-site top-level
     * navigation, and `Strict` would withhold the cookie on the way back, losing
     * the pending authorization request and breaking login outright.
     */
    @Bean
    fun sessionCookieCustomizer(props: SessionCookieProperties) =
        DefaultCookieSerializerCustomizer { serializer ->
            serializer.setSameSite("Lax")
            serializer.setUseHttpOnlyCookie(true)
            serializer.setUseSecureCookie(props.secure)
            serializer.setCookiePath("/")
        }
}
