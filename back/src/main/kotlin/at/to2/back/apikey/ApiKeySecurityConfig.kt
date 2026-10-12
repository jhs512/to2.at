package at.to2.back.apikey

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter

data class ApiKeyPrincipal(val ownerId: Long)

@Configuration
class ApiKeySecurityConfig(private val keys: ApiKeyRepository) {
    @Bean
    @Order(2)
    fun apiKeyChain(http: HttpSecurity): SecurityFilterChain {
        // A supplied credential always wins over a browser session, including
        // when invalid. Never silently fall back to another identity.
        http.securityMatcher { it.getHeader("Authorization") != null }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/me", "/links", "/links/*").hasAuthority("API_OWNER")
                    .anyRequest().denyAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> response.sendError(401) }
            }
            .csrf { it.disable() }
            .addFilterBefore(ApiKeyFilter(keys), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}

private class ApiKeyFilter(private val keys: ApiKeyRepository) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain,
    ) {
        val authorization = request.getHeader("Authorization") ?: ""
        val match = Regex("^Bearer (to2_[A-Za-z0-9_-]{43})$", RegexOption.IGNORE_CASE)
            .matchEntire(authorization)
        val key = match?.groupValues?.get(1)?.let { keys.findByKeyHashAndRevokedAtIsNull(hashApiKey(it)) }
        if (key == null) {
            // Generic JSON, without the supplied credential or OAuth redirect.
            response.status = 401
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.writer.write("""{"message":"유효한 API 키가 필요합니다."}""")
            return
        }
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken.authenticated(
            ApiKeyPrincipal(key.owner.id!!), null, listOf(SimpleGrantedAuthority("API_OWNER")),
        )
        SecurityContextHolder.setContext(context)
        chain.doFilter(request, response)
    }
}
