package ai.govbiz.core.client.bizinfo

import ai.govbiz.core.config.BizInfoClientProperties
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.util.concurrent.TimeoutException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode

@Component
class BizInfoClient(
    @Qualifier("bizInfoRestClient") private val restClient: RestClient,
    private val properties: BizInfoClientProperties,
) {

    fun fetchAll(): List<BizInfoProgramPayload> {
        val serviceKey = properties.decodedServiceKey()
        if (serviceKey.isBlankLikeJava()) {
            throw BizInfoClientException.notConfigured()
        }

        val firstPage = fetchPage(serviceKey, 1)
        if (firstPage.totalCount < 0) {
            throw BizInfoClientException.invalidResponse(
                "BizInfo API returned a negative totalCount",
                null,
            )
        }

        val pageCount = maxOf(1, ceilDiv(firstPage.totalCount, PAGE_SIZE))
        if (pageCount > MAX_PAGES) {
            throw BizInfoClientException.invalidResponse(
                "BizInfo API result exceeded the safe pagination limit",
                null,
            )
        }

        val programs = ArrayList(firstPage.items)
        for (pageNumber in 2..pageCount) {
            programs.addAll(fetchPage(serviceKey, pageNumber).items)
        }
        return java.util.List.copyOf(programs)
    }

    private fun fetchPage(serviceKey: String, pageNumber: Int): Page {
        try {
            val response = restClient.get()
                .uri(
                    PROGRAMS_PATH +
                        "?serviceKey={serviceKey}" +
                        "&pageNo={pageNo}" +
                        "&numOfRows={numOfRows}" +
                        "&dataType=json",
                    serviceKey,
                    pageNumber,
                    PAGE_SIZE,
                )
                .retrieve()
                .onStatus(
                    { statusCode -> statusCode.value() != HttpStatus.OK.value() },
                    { _, clientResponse ->
                        throw BizInfoClientException.upstreamError(
                            "BizInfo API returned unexpected HTTP " +
                                clientResponse.statusCode.value(),
                            null,
                        )
                    },
                )
                .toEntity(JsonNode::class.java)

            val body = response.body
                ?: throw BizInfoClientException.invalidResponse(
                    "BizInfo API returned an empty response",
                    null,
                )
            return decodePage(body)
        } catch (exception: BizInfoClientException) {
            throw exception
        } catch (exception: ResourceAccessException) {
            if (hasTimeoutCause(exception)) {
                throw BizInfoClientException.timeout(exception)
            }
            throw BizInfoClientException.unavailable(exception)
        } catch (exception: RestClientResponseException) {
            throw BizInfoClientException.upstreamError(
                "BizInfo API returned HTTP ${exception.statusCode.value()}",
                exception,
            )
        } catch (exception: RestClientException) {
            throw BizInfoClientException.invalidResponse(
                "BizInfo API response could not be decoded",
                exception,
            )
        } catch (exception: IllegalArgumentException) {
            throw BizInfoClientException.invalidResponse(
                "BizInfo API response could not be decoded",
                exception,
            )
        }
    }

    private fun decodePage(root: JsonNode): Page {
        val response = root.path("response")
        val header = response.path("header")
        if (text(header, "resultCode") != "00") {
            throw BizInfoClientException.upstreamError(
                "BizInfo API returned a non-success result code",
                null,
            )
        }

        val body = response.path("body")
        val totalCount = integer(body, "totalCount")
        if (body.isMissingNode || totalCount == null) {
            throw BizInfoClientException.invalidResponse(
                "BizInfo API response did not contain a valid body",
                null,
            )
        }

        return Page(readItems(body.path("items")), totalCount)
    }

    private fun readItems(itemsNode: JsonNode?): List<BizInfoProgramPayload> {
        if (itemsNode == null || itemsNode.isMissingNode || itemsNode.isNull) {
            return emptyList()
        }

        val itemNode = if (itemsNode.isArray) itemsNode else itemsNode.path("item")
        if (itemNode.isMissingNode || itemNode.isNull) {
            return emptyList()
        }

        val items = ArrayList<BizInfoProgramPayload>()
        if (itemNode.isArray) {
            for (node in itemNode) {
                items.add(toPayload(node))
            }
        } else if (itemNode.isObject) {
            items.add(toPayload(itemNode))
        } else {
            throw BizInfoClientException.invalidResponse(
                "BizInfo API items had an unexpected shape",
                null,
            )
        }
        return java.util.List.copyOf(items)
    }

    private fun toPayload(node: JsonNode): BizInfoProgramPayload {
        if (!node.isObject) {
            throw BizInfoClientException.invalidResponse(
                "BizInfo API item was not an object",
                null,
            )
        }
        return BizInfoProgramPayload(
            text(node, "pblancNm"),
            text(node, "pblancUrl"),
            text(node, "pblancId"),
            text(node, "jrsdInsttNm"),
            text(node, "excInsttNm"),
            text(node, "bsnsSumryCn"),
            text(node, "pldirSportRealmLclasCodeNm"),
            text(node, "creatPnttm"),
            text(node, "reqstBeginEndDe"),
            text(node, "updtPnttm"),
            text(node, "trgetNm"),
            text(node, "hashtags"),
            text(node, "reqstMthPapersCn"),
            text(node, "rceptEngnHmpgUrl"),
        )
    }

    private fun text(node: JsonNode, fieldName: String): String? {
        val value = node.path(fieldName)
        if (value.isMissingNode || value.isNull) {
            return null
        }
        return value.asString().trimLikeJava().ifEmpty { null }
    }

    private fun integer(node: JsonNode, fieldName: String): Int? {
        val value = node.path(fieldName)
        if (!value.isIntegralNumber || !value.canConvertToInt()) {
            return null
        }
        return value.intValue()
    }

    private fun ceilDiv(value: Int, divisor: Int): Int =
        if (value == 0) 0 else ((value - 1) / divisor) + 1

    private fun String.trimLikeJava(): String = trim { it <= ' ' }

    private fun String.isBlankLikeJava(): Boolean =
        codePoints().allMatch(Character::isWhitespace)

    private fun hasTimeoutCause(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (
                current is HttpTimeoutException ||
                current is SocketTimeoutException ||
                current is TimeoutException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private data class Page(
        val items: List<BizInfoProgramPayload>,
        val totalCount: Int,
    )

    companion object {
        const val PROGRAMS_PATH = "/1421000/bizinfo/pblancBsnsService"
        const val PAGE_SIZE = 1_000
        private const val MAX_PAGES = 20
    }
}
