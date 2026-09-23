package at.to2.back.resolve

import at.to2.back.link.Code
import at.to2.back.link.LinkRepository
import at.to2.back.link.LinkState
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * What the edge gets back when it resolves a Code.
 *
 * This is JSON rather than a redirect on purpose: a cross-origin `fetch` cannot
 * read the `Location` of a redirect it followed, so the Interstitial could never
 * learn the Target. Returning the state as data also lets the edge render the
 * right page for a deleted or suspended Link in one round trip.
 */
data class ResolvedCode(
    val code: String,
    val state: LinkState,
    /** Present only for an ACTIVE Link. A suspended Target is never disclosed. */
    val target: String? = null,
)

@RestController
class ResolveController(private val links: LinkRepository) {

    @GetMapping("/to/{code}")
    @Transactional(readOnly = true)
    fun resolve(@PathVariable code: String): ResponseEntity<ResolvedCode> {
        val id = Code.parse(code) ?: return ResponseEntity.notFound().build()
        val link = links.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()

        return when (link.state) {
            LinkState.ACTIVE ->
                ResponseEntity.ok(ResolvedCode(link.code, link.state, link.target))
            // The Target is withheld for anything that is not resolvable, so a
            // blocked destination is not advertised by the block page itself.
            LinkState.DELETED, LinkState.SUSPENDED ->
                ResponseEntity.status(HttpStatus.OK).body(ResolvedCode(link.code, link.state))
        }
    }
}
