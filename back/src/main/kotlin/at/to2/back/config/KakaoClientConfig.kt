package at.to2.back.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

@ConfigurationProperties(prefix = "to2.kakao")
data class KakaoProperties(
    /** Kakao REST API key. Blank locally, where login is simply unavailable. */
    val clientId: String = "",
    val clientSecret: String = "",
)

/**
 * Kakao is not one of the providers the framework knows, so the registration is
 * built here.
 *
 * Two paths on purpose. With credentials we go through OIDC discovery, which is
 * the only reason this integration needs no custom user service: Kakao's ID
 * token carries nickname and email as flat claims, while its plain OAuth2
 * user-info nests them where `user-name-attribute` cannot reach.
 *
 * Without credentials we fall back to a registration built from Kakao's
 * documented endpoints so the application still starts — a developer working on
 * Link management should not need Kakao credentials to run anything at all.
 */
@Configuration
class KakaoClientConfig {

    @Bean
    fun clientRegistrationRepository(props: KakaoProperties): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(
            if (props.clientId.isNotBlank()) discovered(props) else offline(props)
        )

    private fun discovered(props: KakaoProperties): ClientRegistration =
        ClientRegistrations.fromIssuerLocation(ISSUER)
            .registrationId(REGISTRATION_ID)
            .clientId(props.clientId)
            .clientSecret(props.clientSecret)
            .clientName(CLIENT_NAME)
            .scope(*SCOPES)
            .redirectUri(REDIRECT_URI_TEMPLATE)
            // Deliberately NOT setting clientAuthenticationMethod. Kakao
            // advertises only client_secret_post, discovery selects it, and an
            // explicit value here would override that and break token exchange
            // with an opaque 401.
            .build()

    private fun offline(props: KakaoProperties): ClientRegistration =
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(props.clientId.ifBlank { "unconfigured" })
            .clientSecret(props.clientSecret)
            .clientName(CLIENT_NAME)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            // Set explicitly only because there is no discovery document to read
            // it from; this is the value Kakao advertises.
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .redirectUri(REDIRECT_URI_TEMPLATE)
            .scope(*SCOPES)
            .authorizationUri("$ISSUER/oauth/authorize")
            .tokenUri("$ISSUER/oauth/token")
            .jwkSetUri("$ISSUER/.well-known/jwks.json")
            .userInfoUri("https://kapi.kakao.com/v1/oidc/userinfo")
            .userNameAttributeName("sub")
            .issuerUri(ISSUER)
            .build()

    companion object {
        const val REGISTRATION_ID = "kakao"
        const val ISSUER = "https://kauth.kakao.com"
        private const val CLIENT_NAME = "카카오"
        private const val REDIRECT_URI_TEMPLATE = "{baseUrl}/login/oauth2/code/{registrationId}"

        /**
         * Kakao's own consent identifiers. The standard OIDC names `profile` and
         * `email` are not consent items Kakao knows and silently yield nothing.
         */
        private val SCOPES = arrayOf("openid", "profile_nickname", "account_email")
    }
}
