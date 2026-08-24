package ai.govbiz.core.client.bizinfo;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import ai.govbiz.core.config.BizInfoClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class BizInfoClient {

    static final String PROGRAMS_PATH = "/1421000/bizinfo/pblancBsnsService";
    static final int PAGE_SIZE = 1_000;
    private static final int MAX_PAGES = 20;

    private final RestClient restClient;
    private final BizInfoClientProperties properties;

    public BizInfoClient(
            @Qualifier("bizInfoRestClient") RestClient restClient,
            BizInfoClientProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public List<BizInfoProgramPayload> fetchAll() {
        String serviceKey = properties.decodedServiceKey();
        if (serviceKey.isBlank()) {
            throw BizInfoClientException.notConfigured();
        }

        Page firstPage = fetchPage(serviceKey, 1);
        if (firstPage.totalCount() < 0) {
            throw BizInfoClientException.invalidResponse(
                    "BizInfo API returned a negative totalCount",
                    null);
        }

        int pageCount = Math.max(1, ceilDiv(firstPage.totalCount(), PAGE_SIZE));
        if (pageCount > MAX_PAGES) {
            throw BizInfoClientException.invalidResponse(
                    "BizInfo API result exceeded the safe pagination limit",
                    null);
        }

        List<BizInfoProgramPayload> programs = new ArrayList<>(firstPage.items());
        for (int pageNumber = 2; pageNumber <= pageCount; pageNumber++) {
            programs.addAll(fetchPage(serviceKey, pageNumber).items());
        }
        return List.copyOf(programs);
    }

    private Page fetchPage(String serviceKey, int pageNumber) {
        try {
            ResponseEntity<JsonNode> response = restClient.get()
                    .uri(
                            PROGRAMS_PATH
                                    + "?serviceKey={serviceKey}"
                                    + "&pageNo={pageNo}"
                                    + "&numOfRows={numOfRows}"
                                    + "&dataType=json",
                            serviceKey,
                            pageNumber,
                            PAGE_SIZE)
                    .retrieve()
                    .onStatus(
                            statusCode -> statusCode.value() != HttpStatus.OK.value(),
                            (request, clientResponse) -> {
                                throw BizInfoClientException.upstreamError(
                                        "BizInfo API returned unexpected HTTP "
                                                + clientResponse.getStatusCode().value(),
                                        null);
                            })
                    .toEntity(JsonNode.class);

            if (response.getBody() == null) {
                throw BizInfoClientException.invalidResponse(
                        "BizInfo API returned an empty response",
                        null);
            }
            return decodePage(response.getBody());
        } catch (BizInfoClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw BizInfoClientException.timeout(exception);
            }
            throw BizInfoClientException.unavailable(exception);
        } catch (RestClientResponseException exception) {
            throw BizInfoClientException.upstreamError(
                    "BizInfo API returned HTTP " + exception.getStatusCode().value(),
                    exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw BizInfoClientException.invalidResponse(
                    "BizInfo API response could not be decoded",
                    exception);
        }
    }

    private Page decodePage(JsonNode root) {
        JsonNode response = root.path("response");
        JsonNode header = response.path("header");
        if (!"00".equals(text(header, "resultCode"))) {
            throw BizInfoClientException.upstreamError(
                    "BizInfo API returned a non-success result code",
                    null);
        }

        JsonNode body = response.path("body");
        Integer totalCount = integer(body, "totalCount");
        if (body.isMissingNode() || totalCount == null) {
            throw BizInfoClientException.invalidResponse(
                    "BizInfo API response did not contain a valid body",
                    null);
        }

        return new Page(readItems(body.path("items")), totalCount);
    }

    private List<BizInfoProgramPayload> readItems(JsonNode itemsNode) {
        if (itemsNode == null || itemsNode.isMissingNode() || itemsNode.isNull()) {
            return List.of();
        }

        JsonNode itemNode = itemsNode.isArray() ? itemsNode : itemsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }

        List<BizInfoProgramPayload> items = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                items.add(toPayload(node));
            }
        } else if (itemNode.isObject()) {
            items.add(toPayload(itemNode));
        } else {
            throw BizInfoClientException.invalidResponse(
                    "BizInfo API items had an unexpected shape",
                    null);
        }
        return List.copyOf(items);
    }

    private BizInfoProgramPayload toPayload(JsonNode node) {
        if (!node.isObject()) {
            throw BizInfoClientException.invalidResponse(
                    "BizInfo API item was not an object",
                    null);
        }
        return new BizInfoProgramPayload(
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
                text(node, "rceptEngnHmpgUrl"));
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integer(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            return null;
        }
        return value.intValue();
    }

    private static int ceilDiv(int value, int divisor) {
        return value == 0 ? 0 : ((value - 1) / divisor) + 1;
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record Page(List<BizInfoProgramPayload> items, int totalCount) {
    }
}
