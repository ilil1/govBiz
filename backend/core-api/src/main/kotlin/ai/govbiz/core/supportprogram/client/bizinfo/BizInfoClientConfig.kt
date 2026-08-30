package ai.govbiz.core.supportprogram.client.bizinfo

import ai.govbiz.core._common.config.buildRestClient
import java.time.Clock
import java.time.ZoneId
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BizInfoClientProperties::class)
class BizInfoClientConfig {

    @Bean
    fun bizInfoRestClient(
        restClientBuilder: RestClient.Builder,
        properties: BizInfoClientProperties,
    ): RestClient = buildRestClient(
        restClientBuilder,
        properties.baseUrl,
        properties.connectTimeout,
        properties.readTimeout,
    )

    @Bean
    fun seoulClock(): Clock = Clock.system(SEOUL_ZONE)

    companion object {
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
