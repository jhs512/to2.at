package at.to2.back.link

import at.to2.back.config.AppProperties
import java.net.URI
import org.springframework.stereotype.Component

class InvalidTargetException(message: String) : RuntimeException(message)

@Component
class TargetValidator(private val props: AppProperties) {

    /** Returns the Target in the form it will be stored and redirected to. */
    fun validate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw InvalidTargetException("주소를 입력해주세요.")

        val uri =
            try {
                URI(trimmed)
            } catch (_: Exception) {
                throw InvalidTargetException("주소 형식이 올바르지 않습니다.")
            }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw InvalidTargetException("http 또는 https 주소만 등록할 수 있습니다.")
        }

        val host = uri.host?.lowercase() ?: throw InvalidTargetException("주소에 호스트가 없습니다.")

        // A Target pointing back here would send the Visitor into a loop between
        // the Interstitial and itself.
        if (host == props.shortHost || host.endsWith(".${props.shortHost}")) {
            throw InvalidTargetException("이 서비스의 주소는 등록할 수 없습니다.")
        }

        return trimmed
    }
}
