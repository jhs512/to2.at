package at.to2.back

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class BackApplication

fun main(args: Array<String>) {
    runApplication<BackApplication>(*args)
}
