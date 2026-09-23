package at.to2.back.link

import at.to2.back.config.AppProperties
import at.to2.back.owner.OwnerService
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateLinkRequest(val target: String = "")

data class LinkResponse(
    val code: String,
    val shortUrl: String,
    val target: String,
    val state: LinkState,
    val createdAt: Instant,
)

data class ErrorResponse(val message: String)

@RestController
@RequestMapping("/links")
class LinkController(
    private val links: LinkRepository,
    private val ownerService: OwnerService,
    private val validator: TargetValidator,
    private val props: AppProperties,
) {

    @PostMapping
    @Transactional
    fun create(
        auth: Authentication,
        @RequestBody request: CreateLinkRequest,
    ): ResponseEntity<LinkResponse> {
        val owner = ownerService.requireOwner(auth)
        val target = validator.validate(request.target)

        // No deduplication: the same Target twice is two Links. They may be
        // shared in different places and deleted independently.
        val link = links.save(Link(target = target, owner = owner))
        return ResponseEntity.status(HttpStatus.CREATED).body(link.toResponse(props.shortHost))
    }

    // Not read-only: an Owner who has never been seen before is created here.
    // Listing is the first thing a freshly signed-in Owner does, so this is a
    // normal path, not an edge case.
    @GetMapping
    @Transactional
    fun list(auth: Authentication): List<LinkResponse> {
        val owner = ownerService.requireOwner(auth)
        return links
            .findAllByOwnerIdOrderByIdDesc(owner.id!!)
            .filter { it.state != LinkState.DELETED }
            .map { it.toResponse(props.shortHost) }
    }

    @DeleteMapping("/{code}")
    @Transactional
    fun delete(
        auth: Authentication,
        @PathVariable code: String,
    ): ResponseEntity<Void> {
        val owner = ownerService.requireOwner(auth)
        val id = Code.parse(code) ?: return ResponseEntity.notFound().build()
        val link = links.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()

        // Not-found rather than forbidden: telling a stranger that a Code exists
        // but belongs to someone else discloses more than refusing to.
        if (link.owner.id != owner.id || link.state == LinkState.DELETED) {
            return ResponseEntity.notFound().build()
        }

        // A state change, never a row removal, so this Code is never reissued to
        // a different Link — an address already shared must not change meaning.
        link.changeState(LinkState.DELETED)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(InvalidTargetException::class)
    fun onInvalidTarget(e: InvalidTargetException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(e.message ?: "잘못된 주소입니다."))
}

fun Link.toResponse(shortHost: String) =
    LinkResponse(
        code = code,
        shortUrl = "https://$shortHost/$code",
        target = target,
        state = state,
        createdAt = createdAt,
    )
