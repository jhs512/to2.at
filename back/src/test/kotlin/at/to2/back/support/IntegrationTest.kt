package at.to2.back.support

import at.to2.back.TestcontainersConfiguration
import at.to2.back.link.Link
import at.to2.back.link.LinkRepository
import at.to2.back.link.LinkState
import at.to2.back.owner.Owner
import at.to2.back.owner.OwnerRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc

/**
 * Seam A: every backend test drives real HTTP handling through the whole
 * framework stack against a real PostgreSQL of the production major version.
 * Migrations run during context startup, so each test also exercises the
 * migration set we ship.
 *
 * Tests assert status, body and database effect. Behaviour is only ever invoked
 * through this seam; repositories appear to build fixtures or to check what a
 * request left behind, never to perform the thing under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest {

    @Autowired protected lateinit var mvc: MockMvc
    @Autowired protected lateinit var owners: OwnerRepository
    @Autowired protected lateinit var links: LinkRepository
    @Autowired protected lateinit var jdbc: JdbcTemplate

    /** A logged-in Kakao principal for the given subject. */
    protected fun asOwner(kakaoSub: String, nickname: String = "테스터") =
        oidcLogin().idToken {
            it.subject(kakaoSub)
            it.claim("nickname", nickname)
        }

    protected fun clearAll() {
        links.deleteAll()
        owners.deleteAll()
    }

    protected fun anOwner(
        kakaoSub: String = "kakao-${System.nanoTime()}",
        nickname: String = "tester",
    ): Owner = owners.save(Owner(kakaoSub = kakaoSub, nickname = nickname))

    protected fun aLink(
        owner: Owner = anOwner(),
        target: String = "https://example.com/some/long/path",
        state: LinkState = LinkState.ACTIVE,
    ): Link {
        val link = Link(target = target, owner = owner)
        if (state != LinkState.ACTIVE) link.changeState(state)
        return links.save(link)
    }
}
