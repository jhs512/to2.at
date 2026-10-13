package at.to2.back.link

import at.to2.back.config.AppProperties
import at.to2.back.owner.OwnerService
import at.to2.back.owner.OwnerRepository
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

data class CreateLinkRequest(val target: String = "", val requestId: UUID? = null, val name: JsonNode? = null)
// JsonNode preserves omitted versus explicit null for PATCH fields.
data class UpdateLinkRequest(val target: JsonNode? = null, val name: JsonNode? = null)

data class LinkResponse(
    val code: String,
    val shortUrl: String,
    val target: String,
    val state: LinkState,
    val createdAt: Instant,
    val name: String?,
)

data class ErrorResponse(val message: String)

@RestController
@RequestMapping("/links")
class LinkController(
    private val links: LinkRepository,
    private val ownerService: OwnerService,
    private val validator: TargetValidator,
    private val props: AppProperties,
    private val owners: OwnerRepository,
) {

    @PostMapping
    @Transactional
    fun create(
        auth: Authentication,
        @RequestBody request: CreateLinkRequest,
    ): ResponseEntity<LinkResponse> {
        val owner = ownerService.requireOwner(auth)
        val target = validator.validate(request.target)
        val name = validateName(request.name)

        if (request.requestId != null) {
            // Serialize this Owner's external creates before looking up the
            // request: refreshes and concurrent tabs must not allocate twice.
            owners.lockForLinkCreation(owner.id!!)
            val existing = links.findByOwnerIdAndCreateRequestId(owner.id!!, request.requestId)
            if (existing != null) {
                if (existing.target != target) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).build()
                }
                return ResponseEntity.ok(existing.toResponse(props.shortHost))
            }
        }

        // Ordinary creates have no requestId and still allocate separate Links.
        val link = links.save(Link(target = target, owner = owner, createRequestId = request.requestId, name = name))
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

    @PatchMapping("/{code}")
    @Transactional
    fun update(
        auth: Authentication,
        @PathVariable code: String,
        @RequestBody request: UpdateLinkRequest,
    ): ResponseEntity<LinkResponse> {
        val owner = ownerService.requireOwner(auth)
        val id = Code.parse(code) ?: return ResponseEntity.notFound().build()
        val link = links.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        if (link.owner.id != owner.id || link.state == LinkState.DELETED) {
            return ResponseEntity.notFound().build()
        }
        if (request.target == null && request.name == null) {
            throw InvalidTargetException("대상 URL 또는 이름을 전달해주세요.")
        }
        val target = request.target?.let {
            if (!it.isString) throw InvalidTargetException("대상 URL은 문자열이어야 합니다.")
            validator.validate(it.asString())
        }
        val name = validateName(request.name)
        // A conditional field-only update also handles deletion between read
        // and write, and never overwrites moderation state with a stale copy.
        if (links.updateOwnedDetails(id, owner.id!!, target, request.name != null, name) == 0) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(links.findById(id).orElseThrow().toResponse(props.shortHost))
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

    @ExceptionHandler(InvalidLinkNameException::class)
    fun onInvalidName(e: InvalidLinkNameException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(e.message ?: "잘못된 이름입니다."))
}

class InvalidLinkNameException(message: String) : RuntimeException(message)

private fun validateName(value: JsonNode?): String? {
    if (value == null || value.isNull) return null
    if (!value.isString) throw InvalidLinkNameException("이름은 문자열이어야 합니다.")
    val name = value.asString().trim()
    if (name.codePointCount(0, name.length) > 100) {
        throw InvalidLinkNameException("이름은 최대 100자입니다.")
    }
    return name.ifEmpty { null }
}

fun Link.toResponse(shortHost: String) =
    LinkResponse(
        code = code,
        shortUrl = "https://$shortHost/$code",
        target = target,
        state = state,
        createdAt = createdAt,
        name = name,
    )
