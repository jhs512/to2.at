package at.to2.back.owner

import at.to2.back.admin.LocalAccountRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Signed in, but not as someone who can own Links.
 *
 * The status lives on the exception rather than on a handler in one controller:
 * scoped to a controller, the same refusal came back as 403 from `/links` and
 * 500 from `/me`.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
class NotAnOwnerException : RuntimeException("이 계정으로는 링크를 만들 수 없습니다.")

/**
 * Turns a Kakao principal into an Owner, creating one on first sight.
 *
 * Upserting here rather than in a login callback means a session that outlives
 * its row still resolves, and it keeps Owner creation observable from the API.
 */
@Service
class OwnerService(
    private val owners: OwnerRepository,
    private val accounts: LocalAccountRepository,
) {

    /**
     * Resolves whoever is signed in to an Owner.
     *
     * Two principal shapes reach here. In production it is always a Kakao
     * principal. Locally it can also be a seeded username/password account,
     * which is the whole point of those accounts: Owner flows have to be
     * exercisable without going through Kakao. An account with no Owner — the
     * Operator — resolves to null, which is how administration stays separate
     * from ownership.
     */
    @Transactional
    fun fromAuthentication(auth: Authentication?): Owner? =
        when (val principal = auth?.principal) {
            is OidcUser -> fromPrincipal(principal)
            is UserDetails ->
                accounts.findByUsername(principal.username)?.ownerId?.let {
                    owners.findById(it).orElse(null)
                }
            else -> null
        }

    /**
     * The same resolution, for the endpoints that only make sense for an Owner.
     * The Operator has no Owner row, so this is what keeps administration from
     * quietly acquiring the ability to own Links.
     */
    fun requireOwner(auth: Authentication?): Owner =
        fromAuthentication(auth) ?: throw NotAnOwnerException()

    @Transactional
    fun fromPrincipal(principal: OidcUser): Owner {
        // The OIDC subject is the only stable identity Kakao gives us. Email is
        // optional even on a Biz app, so it must never be the key.
        val sub =
            requireNotNull(principal.subject) { "Kakao returned an ID token with no subject" }
        val nickname = principal.getClaimAsString(StandardClaimNames.NICKNAME) ?: "이름없음"
        val email = principal.getClaimAsString(StandardClaimNames.EMAIL)
        // Kakao only sends an email it considers usable, but the claim is still
        // advisory: treat it as profile data, never as proof of identity.
        val verified = principal.getClaimAsBoolean(StandardClaimNames.EMAIL_VERIFIED) ?: false

        val existing = owners.findByKakaoSub(sub)
        if (existing != null) {
            existing.nickname = nickname
            existing.email = email
            existing.emailVerified = verified
            return existing
        }
        return owners.save(
            Owner(kakaoSub = sub, nickname = nickname, email = email, emailVerified = verified)
        )
    }
}
