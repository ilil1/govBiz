package ai.govbiz.core.supportprogram.client.bizinfo

import ai.govbiz.core.supportprogram.client.bizinfo.exception.BizInfoClientException
import ai.govbiz.core.supportprogram.client.bizinfo.mapper.BizInfoProgramMapper
import ai.govbiz.core.supportprogram.service.dto.CatalogSupportProgram
import ai.govbiz.core.supportprogram.service.search.SupportProgramCatalog
import ai.govbiz.core.supportprogram.service.search.SupportProgramSearchException
import java.time.Clock
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/** 기업마당 원본 조회와 검색 후보 변환을 연결합니다. */
@Component
class BizInfoSupportProgramCatalog(
    private val client: BizInfoClient,
    @param:Qualifier("seoulClock") private val clock: Clock,
) : SupportProgramCatalog {

    override fun load(): List<CatalogSupportProgram> =
        try {
            BizInfoProgramMapper.mapAndDeduplicate(
                payloads = client.fetchAll(),
                today = LocalDate.now(clock),
            )
        } catch (exception: BizInfoClientException) {
            throw SupportProgramSearchException.fromCatalog(
                failure = when (exception.failure) {
                    BizInfoClientException.Failure.NOT_CONFIGURED ->
                        SupportProgramSearchException.Failure.NOT_CONFIGURED
                    BizInfoClientException.Failure.UPSTREAM_ERROR ->
                        SupportProgramSearchException.Failure.UPSTREAM_ERROR
                    BizInfoClientException.Failure.INVALID_RESPONSE ->
                        SupportProgramSearchException.Failure.INVALID_RESPONSE
                    BizInfoClientException.Failure.UNAVAILABLE ->
                        SupportProgramSearchException.Failure.UNAVAILABLE
                    BizInfoClientException.Failure.TIMEOUT ->
                        SupportProgramSearchException.Failure.TIMEOUT
                },
                message = exception.message,
                cause = exception,
            )
        }
}
