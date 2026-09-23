package at.to2.back

import at.to2.back.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class BackApplicationTests : IntegrationTest() {

    @Test
    fun `the application starts and reports healthy`() {
        // Also proves the Flyway migrations applied cleanly against real
        // PostgreSQL and that Hibernate's schema validation agrees with them.
        mvc.get("/actuator/health").andExpect { status { isOk() } }
    }
}
