package at.to2.back.apikey

import at.to2.back.owner.OwnerService
import at.to2.back.owner.Owner
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class CreateApiKeyRequest(val name: String = "")
data class ApiKeyMetadata(val id: Long, val name: String, val createdAt: Instant, val revokedAt: Instant?)
// Not a data class: a generated toString must never contain the raw key.
class IssuedApiKey(val id: Long, val name: String, val createdAt: Instant, val key: String)

internal fun hashApiKey(raw: String): String =
    MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

@RestController
@RequestMapping("/api-keys")
class ApiKeyController(private val keys: ApiKeyRepository, private val owners: OwnerService) {
    private val random = SecureRandom()

    private fun owner(auth: Authentication): Owner {
        if (auth.principal !is OidcUser) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "카카오 로그인으로 API 키를 관리해주세요.")
        }
        return owners.requireOwner(auth)
    }

    @PostMapping
    @Transactional
    fun create(auth: Authentication, @RequestBody request: CreateApiKeyRequest): ResponseEntity<IssuedApiKey> {
        val owner = owner(auth)
        val name = request.name.trim()
        if (name.isEmpty() || name.length > 80) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API 키 이름은 1~80자여야 합니다.")
        }
        val bytes = ByteArray(32).also(random::nextBytes)
        val raw = "to2_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val key = keys.save(ApiKey(owner, name, hashApiKey(raw)))
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
            .body(IssuedApiKey(key.id!!, key.name, key.createdAt, raw))
    }

    @GetMapping
    @Transactional
    fun list(auth: Authentication): ResponseEntity<List<ApiKeyMetadata>> =
        ResponseEntity.ok().header("Cache-Control", "no-store").body(
            keys.findAllByOwnerIdOrderByIdDesc(owner(auth).id!!).map {
                ApiKeyMetadata(it.id!!, it.name, it.createdAt, it.revokedAt)
            }
        )

    @DeleteMapping("/{id}")
    @Transactional
    fun revoke(auth: Authentication, @PathVariable id: Long): ResponseEntity<Void> {
        val key = keys.findByIdAndOwnerId(id, owner(auth).id!!)
            ?: return ResponseEntity.notFound().build()
        if (key.revokedAt == null) key.revokedAt = Instant.now()
        return ResponseEntity.noContent().build()
    }
}
