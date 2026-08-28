package ai.govbiz.core.config

import java.net.http.HttpClient
import java.time.Clock
import java.time.ZoneId
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.boot.http.client.HttpRedirects
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BizInfoClientProperties::class)
class BizInfoClientConfig {

    @Bean
    @Qualifier("bizInfoRestClient")
    fun bizInfoRestClient(
        restClientBuilder: RestClient.Builder,
        properties: BizInfoClientProperties,
    ): RestClient {
        val settings = HttpClientSettings.defaults()
            .withConnectTimeout(properties.connectTimeout)
            .withReadTimeout(properties.readTimeout)
            .withRedirects(HttpRedirects.DONT_FOLLOW)
        val requestFactory: ClientHttpRequestFactory = ClientHttpRequestFactoryBuilder.jdk()
            .withHttpClientCustomizer { builder ->
                builder.version(HttpClient.Version.HTTP_1_1)
            }
            .build(settings)

        return restClientBuilder
            .baseUrl(properties.baseUrl.toString())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    @Bean
    @Qualifier("seoulClock")
    fun seoulClock(): Clock = Clock.system(SEOUL_ZONE)

    companion object {
        @JvmField
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
