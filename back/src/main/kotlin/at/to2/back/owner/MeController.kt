package at.to2.back.owner

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class MeResponse(val nickname: String, val email: String?)

@RestController
class MeController(private val ownerService: OwnerService) {

    /**
     * Who is signed in. Also the point at which an Owner row first appears:
     * there is no separate sign-up step.
     */
    @GetMapping("/me")
    fun me(auth: Authentication): MeResponse {
        val owner = ownerService.requireOwner(auth)
        // An unverified address is profile noise at best; do not hand it out as
        // though the service vouched for it.
        return MeResponse(owner.nickname, owner.email?.takeIf { owner.emailVerified })
    }
}
