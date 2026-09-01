package ai.govbiz.core.supportprogram.config

import java.time.Clock
import java.time.ZoneId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class SupportProgramConfig {

    @Bean
    fun seoulClock(): Clock = Clock.system(SEOUL_ZONE)

    companion object {
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
