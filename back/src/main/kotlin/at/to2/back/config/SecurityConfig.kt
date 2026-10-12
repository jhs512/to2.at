package at.to2.back.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@ConfigurationProperties(prefix = "to2")
data class AppProperties(
    /** Origin the SPA is served from. The only origin CORS allows. */
    val siteOrigin: String,
    /** Host of the short address, rejected as a Target to prevent loops. */
    val shortHost: String,
)

@Configuration
class SecurityConfig {

    /**
     * The public chain: Owners sign in with Kakao, and nothing else.
     *
     * It carries no `securityMatcher`, so it must stay the last chain
     * registered — the framework fails startup if an "any request" chain is
     * followed by another one.
     *
     * Kakao is the only registered client, which makes the entry point redirect
     * straight to Kakao instead of rendering a login page. That is exactly what
     * we want here (ADR 0005): no intermediate screen. The Operator's password
     * form lives on its own chain precisely so this behaviour cannot swallow it.
     */
    @Bean
    fun publicChain(http: HttpSecurity, props: AppProperties): SecurityFilterChain {
        http.cors { it.configurationSource(corsSource(props)) }
            .authorizeHttpRequests {
                it.requestMatchers("/to/**", "/actuator/health")
                    .permitAll()
                    // Reporting must have no barrier: the Visitor looking at a
                    // suspicious Link is rarely signed in, and requiring it
                    // would simply mean abuse goes unreported.
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/reports")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .oauth2Login {
                // Land back on the SPA, always. The default sends the Owner to
                // "/" on THIS host, which serves nothing — the site lives on
                // another origin (ADR 0001). `alwaysUse` because there is never
                // a saved request worth resuming here: the only pages an Owner
                // gets bounced from are the SPA's, and those are not ours.
                it.defaultSuccessUrl(props.siteOrigin, true)
            }
            .logout { it.logoutSuccessUrl(props.siteOrigin) }
            // The SPA is the only browser client and it is on another origin, so
            // there are no server-rendered forms to protect. Session cookies are
            // SameSite=Lax, which is what keeps cross-site POSTs from carrying
            // credentials here.
            .csrf { it.disable() }
        return http.build()
    }

    /**
     * Deliberately not a bean. `HandlerMappingIntrospector` also implements
     * [CorsConfigurationSource], so exposing one here makes injection by type
     * ambiguous; the chain is the only thing that needs it.
     */
    private fun corsSource(props: AppProperties): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                // Exactly the site origin, never a wildcard: credentials are sent.
                allowedOrigins = listOf(props.siteOrigin)
                allowedMethods = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Content-Type")
                allowCredentials = true
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
