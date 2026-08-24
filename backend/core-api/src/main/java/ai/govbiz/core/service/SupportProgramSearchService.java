package ai.govbiz.core.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.govbiz.core.client.bizinfo.BizInfoClient;
import ai.govbiz.core.client.bizinfo.BizInfoClientException;
import ai.govbiz.core.client.bizinfo.BizInfoProgramPayload;
import ai.govbiz.core.domain.support.SupportProgram;
import ai.govbiz.core.domain.support.SupportProgramStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class SupportProgramSearchService {

    private static final int RESULT_LIMIT = 5;
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration MAX_STALE_AGE = Duration.ofHours(24);
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}[-./]\\d{2}[-./]\\d{2}");
    private static final Pattern HTML_BLOCK = Pattern.compile(
            "(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>|</p>|</li>");
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern QUERY_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}&]+");
    private static final Pattern ASCII_QUERY_TERM = Pattern.compile("[a-z0-9&]+");

    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "공고", "사업", "지원", "지원사업", "정부지원", "정부지원사업",
            "찾아줘", "알려줘", "보여줘", "추천해줘", "추천", "해줘", "주세요",
            "현재", "접수", "중인", "가능한", "가능", "신청", "프로그램");

    private static final Map<String, String> REGION_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울"), Map.entry("서울특별시", "서울"),
            Map.entry("부산", "부산"), Map.entry("부산광역시", "부산"),
            Map.entry("대구", "대구"), Map.entry("대구광역시", "대구"),
            Map.entry("인천", "인천"), Map.entry("인천광역시", "인천"),
            Map.entry("광주", "광주"), Map.entry("광주광역시", "광주"),
            Map.entry("대전", "대전"), Map.entry("대전광역시", "대전"),
            Map.entry("울산", "울산"), Map.entry("울산광역시", "울산"),
            Map.entry("세종", "세종"), Map.entry("세종특별자치시", "세종"),
            Map.entry("경기", "경기"), Map.entry("경기도", "경기"),
            Map.entry("강원", "강원"), Map.entry("강원특별자치도", "강원"),
            Map.entry("충북", "충북"), Map.entry("충청북도", "충북"),
            Map.entry("충남", "충남"), Map.entry("충청남도", "충남"),
            Map.entry("전북", "전북"), Map.entry("전북특별자치도", "전북"),
            Map.entry("전남", "전남"), Map.entry("전라남도", "전남"),
            Map.entry("경북", "경북"), Map.entry("경상북도", "경북"),
            Map.entry("경남", "경남"), Map.entry("경상남도", "경남"),
            Map.entry("제주", "제주"), Map.entry("제주특별자치도", "제주"),
            Map.entry("전국", "전국"));

    private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("ai", "AI"), Map.entry("인공지능", "AI"),
            Map.entry("창업", "창업"), Map.entry("기술", "기술"),
            Map.entry("기술개발", "기술"), Map.entry("r&d", "기술"),
            Map.entry("수출", "수출"), Map.entry("해외진출", "수출"),
            Map.entry("경영", "경영"), Map.entry("금융", "금융"),
            Map.entry("인력", "인력"), Map.entry("내수", "내수"),
            Map.entry("판로", "내수"), Map.entry("제조", "제조"),
            Map.entry("콘텐츠", "콘텐츠"), Map.entry("소상공인", "소상공인"));

    private static final Map<String, List<String>> CATEGORY_VARIANTS = Map.ofEntries(
            Map.entry("AI", List.of("ai", "인공지능")),
            Map.entry("창업", List.of("창업", "스타트업")),
            Map.entry("기술", List.of("기술", "기술개발", "r&d", "연구개발")),
            Map.entry("수출", List.of("수출", "해외진출")),
            Map.entry("경영", List.of("경영")),
            Map.entry("금융", List.of("금융", "융자", "보증")),
            Map.entry("인력", List.of("인력", "채용", "고용")),
            Map.entry("내수", List.of("내수", "판로", "유통")),
            Map.entry("제조", List.of("제조", "스마트공장")),
            Map.entry("콘텐츠", List.of("콘텐츠")),
            Map.entry("소상공인", List.of("소상공인")));

    private final BizInfoClient client;
    private final AiSearchIntentService aiSearchIntentService;
    private final Clock clock;
    private final Object cacheLock = new Object();
    private volatile CatalogCache cache;

    public SupportProgramSearchService(
            BizInfoClient client,
            AiSearchIntentService aiSearchIntentService,
            @Qualifier("seoulClock") Clock clock
    ) {
        this.client = client;
        this.aiSearchIntentService = aiSearchIntentService;
        this.clock = clock;
    }

    public SupportProgramSearchResult search(String rawQuery, boolean acceptingOnly) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        SearchIntent localIntent = SearchIntent.from(query);
        SearchIntent intent = query.isBlank()
                ? localIntent
                : aiSearchIntentService.analyze(query, acceptingOnly)
                        .map(analyzed -> localIntent.merge(analyzed, query))
                        .orElse(localIntent);

        List<ScoredProgram> scored = catalog().stream()
                .filter(candidate -> !acceptingOnly
                        || candidate.program().status() == SupportProgramStatus.OPEN)
                .map(candidate -> score(candidate, intent))
                .filter(scoredProgram -> intent.terms().isEmpty() || scoredProgram.score() > 0)
                .sorted(Comparator.comparingInt(ScoredProgram::score)
                        .reversed()
                        .thenComparing(
                                scoredProgram -> scoredProgram.candidate().sortTimestamp(),
                                Comparator.reverseOrder()))
                .limit(RESULT_LIMIT)
                .toList();

        List<SupportProgram> programs = scored.stream()
                .map(this::withMatchedReasons)
                .toList();
        return new SupportProgramSearchResult(query, programs);
    }

    private List<IndexedProgram> catalog() {
        Instant now = clock.instant();
        CatalogCache current = cache;
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(now)) {
            return current.programs();
        }

        synchronized (cacheLock) {
            current = cache;
            if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(now)) {
                return current.programs();
            }

            try {
                List<IndexedProgram> refreshed = mapAndDeduplicate(client.fetchAll());
                cache = new CatalogCache(refreshed, now);
                return refreshed;
            } catch (BizInfoClientException exception) {
                if (current != null
                        && current.fetchedAt().plus(MAX_STALE_AGE).isAfter(now)) {
                    return current.programs();
                }
                throw SupportProgramSearchException.fromClient(exception);
            }
        }
    }

    private List<IndexedProgram> mapAndDeduplicate(List<BizInfoProgramPayload> payloads) {
        Map<String, IndexedProgram> programs = new LinkedHashMap<>();
        for (BizInfoProgramPayload payload : payloads) {
            toIndexedProgram(payload).ifPresent(program ->
                    programs.putIfAbsent(program.program().id(), program));
        }
        return List.copyOf(programs.values());
    }

    private Optional<IndexedProgram> toIndexedProgram(BizInfoProgramPayload payload) {
        if (payload == null || isBlank(payload.id()) || isBlank(payload.title())) {
            return Optional.empty();
        }

        String sourceUrl = officialSourceUrl(payload.sourceUrl());
        if (sourceUrl == null) {
            return Optional.empty();
        }

        String applicationPeriod = firstPresent(payload.applicationPeriod(), "정보 없음");
        DateRange dates = parseDates(applicationPeriod);
        SupportProgramStatus status = determineStatus(
                applicationPeriod,
                dates,
                LocalDate.now(clock));
        List<String> categories = categories(payload.category());
        List<String> regions = regions(payload.hashtags());
        String summary = plainText(payload.summaryHtml());
        String organization = firstPresent(
                payload.executingOrganization(),
                payload.jurisdictionOrganization(),
                "정보 없음");

        SupportProgram program = new SupportProgram(
                payload.id().trim(),
                payload.title().trim(),
                organization,
                summary.isBlank() ? "정보 없음" : summary,
                categories,
                regions,
                firstPresent(payload.target(), "정보 없음"),
                "정보 없음",
                applicationPeriod,
                dates.start(),
                dates.end(),
                status,
                "기업마당",
                sourceUrl,
                List.of());

        String hashtags = normalize(payload.hashtags());
        String searchable = normalize(String.join(" ",
                program.title(),
                program.organization(),
                program.summary(),
                program.targetDescription(),
                String.join(" ", program.categories()),
                String.join(" ", program.regions()),
                textOrEmpty(payload.hashtags()),
                textOrEmpty(payload.applicationMethod())));
        return Optional.of(new IndexedProgram(
                program,
                normalize(program.title()),
                normalize(String.join(" ", categories)),
                normalize(String.join(" ", regions)),
                normalize(program.targetDescription()),
                normalize(program.summary()),
                normalize(program.organization()),
                hashtags,
                searchable,
                firstPresent(payload.updatedAt(), payload.createdAt(), "")));
    }

    private ScoredProgram score(IndexedProgram candidate, SearchIntent intent) {
        if (intent.terms().isEmpty()) {
            return new ScoredProgram(candidate, 1, List.of());
        }

        int score = 0;
        boolean regionMatched = false;
        List<QueryTerm> matches = new ArrayList<>();
        for (QueryTerm term : intent.terms()) {
            if (term.kind() == TermKind.REGION && regionMatched) {
                continue;
            }
            int termScore = scoreTerm(candidate, term);
            if (termScore > 0) {
                score += termScore;
                matches.add(term);
                if (term.kind() == TermKind.REGION) {
                    regionMatched = true;
                }
            }
        }
        return new ScoredProgram(candidate, score, List.copyOf(matches));
    }

    private int scoreTerm(IndexedProgram candidate, QueryTerm term) {
        if (term.kind() == TermKind.REGION) {
            if (candidate.program().regions().contains("전국")
                    || candidate.program().regions().contains(term.label())) {
                return 12;
            }
        }
        if (term.kind() == TermKind.CATEGORY
                && candidate.program().categories().stream()
                        .map(SupportProgramSearchService::normalize)
                        .anyMatch(normalize(term.label())::equals)) {
            return 11;
        }
        if (term.kind() == TermKind.TARGET) {
            int targetScore = term.variants().stream()
                    .map(SupportProgramSearchService::normalize)
                    .filter(Predicate.not(String::isBlank))
                    .anyMatch(candidate.target()::contains)
                    ? 8
                    : 0;
            if (targetScore > 0) {
                return targetScore;
            }
        }

        int best = 0;
        for (String variant : term.variants()) {
            String normalizedVariant = normalize(variant);
            if (normalizedVariant.isBlank()) {
                continue;
            }
            if (candidate.title().contains(normalizedVariant)) {
                best = Math.max(best, 9);
            }
            if (candidate.categories().contains(normalizedVariant)) {
                best = Math.max(best, 7);
            }
            if (candidate.regions().contains(normalizedVariant)
                    || candidate.hashtags().contains(normalizedVariant)) {
                best = Math.max(best, 6);
            }
            if (candidate.target().contains(normalizedVariant)) {
                best = Math.max(best, 4);
            }
            if (candidate.summary().contains(normalizedVariant)) {
                best = Math.max(best, 3);
            }
            if (candidate.organization().contains(normalizedVariant)
                    || candidate.searchable().contains(normalizedVariant)) {
                best = Math.max(best, 2);
            }
        }
        return best;
    }

    private SupportProgram withMatchedReasons(ScoredProgram scored) {
        SupportProgram program = scored.candidate().program();
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        for (QueryTerm term : scored.matches()) {
            String reason = switch (term.kind()) {
                case REGION -> term.label() + " 지역";
                case CATEGORY -> term.label() + " 분야";
                case TARGET -> "지원대상 ‘" + term.label() + "’";
                case TEXT -> "‘" + term.label() + "’ 관련";
            };
            reasons.add(reason);
            if (reasons.size() == 3) {
                break;
            }
        }
        if (program.status() == SupportProgramStatus.OPEN && reasons.size() < 3) {
            reasons.add("현재 접수 중");
        }
        if (reasons.isEmpty()) {
            reasons.add("기업마당 공식 공고");
        }

        return new SupportProgram(
                program.id(),
                program.title(),
                program.organization(),
                program.summary(),
                program.categories(),
                program.regions(),
                program.targetDescription(),
                program.supportAmount(),
                program.applicationPeriod(),
                program.applicationStartDate(),
                program.applicationEndDate(),
                program.status(),
                program.sourceName(),
                program.sourceUrl(),
                List.copyOf(reasons));
    }

    private static DateRange parseDates(String applicationPeriod) {
        List<LocalDate> dates = new ArrayList<>(2);
        Matcher matcher = ISO_DATE.matcher(applicationPeriod);
        while (matcher.find() && dates.size() < 2) {
            try {
                dates.add(LocalDate.parse(matcher.group().replace('.', '-').replace('/', '-')));
            } catch (DateTimeParseException ignored) {
                // Invalid upstream date text remains visible through applicationPeriod.
            }
        }
        if (dates.size() >= 2) {
            return new DateRange(dates.get(0), dates.get(1));
        }
        if (dates.size() == 1) {
            String normalized = normalize(applicationPeriod);
            if (normalized.contains("까지") && !isRollingPeriod(normalized)) {
                return new DateRange(null, dates.get(0));
            }
            if (normalized.contains("부터") || isRollingPeriod(normalized)) {
                return new DateRange(dates.get(0), null);
            }
        }
        return new DateRange(null, null);
    }

    private static SupportProgramStatus determineStatus(
            String applicationPeriod,
            DateRange dates,
            LocalDate today
    ) {
        if (dates.start() != null && today.isBefore(dates.start())) {
            return SupportProgramStatus.UPCOMING;
        }
        if (dates.end() != null && today.isAfter(dates.end())) {
            return SupportProgramStatus.CLOSED;
        }
        if (dates.start() != null && dates.end() != null) {
            return SupportProgramStatus.OPEN;
        }

        String normalized = normalize(applicationPeriod);
        if (isUpcomingPeriod(normalized)) {
            return SupportProgramStatus.UPCOMING;
        }
        if (isRollingPeriod(normalized)) {
            return SupportProgramStatus.OPEN;
        }
        if (dates.end() != null) {
            return SupportProgramStatus.OPEN;
        }
        if (isExplicitlyClosed(normalized)) {
            return SupportProgramStatus.CLOSED;
        }
        return SupportProgramStatus.UNKNOWN;
    }

    private static boolean isRollingPeriod(String normalizedPeriod) {
        return containsAny(normalizedPeriod,
                "예산 소진", "예산소진", "상시", "선착순", "모집 완료시", "모집완료시",
                "모집 마감시", "모집마감시", "수시", "정원 마감", "정원마감",
                "규모 마감", "규모마감", "소진시", "완료시");
    }

    private static boolean isUpcomingPeriod(String normalizedPeriod) {
        return containsAny(normalizedPeriod, "추후 공지", "추후공지", "접수 예정", "접수예정");
    }

    private static boolean isExplicitlyClosed(String normalizedPeriod) {
        return containsAny(normalizedPeriod, "접수 종료", "접수종료", "모집 종료", "모집종료", "마감 완료");
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> categories(String category) {
        if (isBlank(category)) {
            return List.of();
        }
        return Pattern.compile("[,/·>]")
                .splitAsStream(category)
                .map(String::trim)
                .filter(Predicate.not(String::isBlank))
                .distinct()
                .toList();
    }

    private static List<String> regions(String hashtags) {
        if (isBlank(hashtags)) {
            return List.of();
        }

        LinkedHashSet<String> regions = new LinkedHashSet<>();
        for (String hashtag : hashtags.split(",")) {
            String normalized = hashtag.trim();
            if ("전남광주".equals(normalized)) {
                regions.add("광주");
                regions.add("전남");
                continue;
            }
            String region = REGION_ALIASES.get(normalized);
            if (region != null) {
                regions.add(region);
            }
        }
        if (regions.contains("전국") || regions.size() >= 10) {
            return List.of("전국");
        }
        return List.copyOf(regions);
    }

    private static String plainText(String html) {
        if (isBlank(html)) {
            return "";
        }
        String withoutBlocks = HTML_BLOCK.matcher(html).replaceAll(" ");
        String withBreaks = HTML_BREAK.matcher(withoutBlocks).replaceAll(" ");
        String withoutTags = HTML_TAG.matcher(withBreaks).replaceAll(" ");
        return HtmlUtils.htmlUnescape(withoutTags)
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String officialSourceUrl(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost();
            boolean supportedScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme());
            boolean officialHost = host != null
                    && ("bizinfo.go.kr".equalsIgnoreCase(host)
                    || host.toLowerCase(Locale.ROOT).endsWith(".bizinfo.go.kr"));
            return supportedScheme && officialHost ? uri.toString() : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private enum TermKind {
        REGION,
        CATEGORY,
        TARGET,
        TEXT
    }

    private record QueryTerm(String label, List<String> variants, TermKind kind) {
    }

    private record SearchIntent(List<QueryTerm> terms) {

        SearchIntent merge(AnalyzedSearchIntent analyzed, String rawQuery) {
            String normalizedQuery = normalize(rawQuery);
            LinkedHashMap<String, QueryTerm> merged = new LinkedHashMap<>();
            for (QueryTerm term : terms) {
                merged.putIfAbsent(termKey(term), term);
            }

            Set<String> locallyDetectedRegions = terms.stream()
                    .filter(term -> term.kind() == TermKind.REGION)
                    .map(QueryTerm::label)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (String regionValue : analyzed.regions()) {
                String region = REGION_ALIASES.get(regionValue);
                if (region != null && locallyDetectedRegions.contains(region)) {
                    addTerm(merged, new QueryTerm(
                            region,
                            List.of(regionValue, region),
                            TermKind.REGION));
                }
            }
            for (String categoryValue : analyzed.categories()) {
                String category = CATEGORY_ALIASES.get(normalize(categoryValue));
                if (category != null && categoryIsGrounded(category, normalizedQuery)) {
                    addTerm(merged, new QueryTerm(
                            category,
                            CATEGORY_VARIANTS.getOrDefault(category, List.of(categoryValue)),
                            TermKind.CATEGORY));
                }
            }
            for (String targetTerm : analyzed.targetTerms()) {
                if (queryContains(normalizedQuery, targetTerm)) {
                    addTerm(merged, new QueryTerm(
                            targetTerm,
                            List.of(targetTerm),
                            TermKind.TARGET));
                }
            }
            for (String keyword : analyzed.keywords()) {
                if (queryContains(normalizedQuery, keyword)) {
                    addTerm(merged, new QueryTerm(
                            keyword,
                            List.of(keyword),
                            TermKind.TEXT));
                }
            }
            return new SearchIntent(List.copyOf(merged.values()));
        }

        private static boolean categoryIsGrounded(String category, String normalizedQuery) {
            return CATEGORY_VARIANTS.getOrDefault(category, List.of(category)).stream()
                    .anyMatch(variant -> queryContains(normalizedQuery, variant));
        }

        private static boolean queryContains(String normalizedQuery, String value) {
            String normalizedValue = normalize(value);
            if (normalizedValue.isBlank()) {
                return false;
            }
            if (ASCII_QUERY_TERM.matcher(normalizedValue).matches()) {
                return QUERY_SEPARATOR.splitAsStream(normalizedQuery)
                        .anyMatch(normalizedValue::equals);
            }
            return normalizedQuery.contains(normalizedValue);
        }

        private static void addTerm(
                LinkedHashMap<String, QueryTerm> terms,
                QueryTerm term
        ) {
            terms.putIfAbsent(termKey(term), term);
        }

        private static String termKey(QueryTerm term) {
            return term.kind().name() + ":" + normalize(term.label());
        }

        static SearchIntent from(String query) {
            String normalized = normalize(query);
            LinkedHashMap<String, QueryTerm> terms = new LinkedHashMap<>();
            for (String token : QUERY_SEPARATOR.split(normalized)) {
                if (token.isBlank() || QUERY_STOP_WORDS.contains(token)) {
                    continue;
                }

                String regionToken = regionToken(token);
                String region = REGION_ALIASES.get(regionToken);
                if (region != null) {
                    terms.putIfAbsent("region:" + region,
                            new QueryTerm(region, List.of(token, regionToken, region), TermKind.REGION));
                    continue;
                }

                String category = CATEGORY_ALIASES.get(token);
                if (category != null) {
                    terms.putIfAbsent("category:" + category,
                            new QueryTerm(
                                    category,
                                    CATEGORY_VARIANTS.getOrDefault(category, List.of(token)),
                                    TermKind.CATEGORY));
                    continue;
                }

                List<String> variants = new ArrayList<>();
                variants.add(token);
                if (token.endsWith("지원") && token.length() > 2) {
                    variants.add(token.substring(0, token.length() - 2));
                }
                terms.putIfAbsent("text:" + token,
                        new QueryTerm(token, List.copyOf(variants), TermKind.TEXT));
            }
            return new SearchIntent(List.copyOf(terms.values()));
        }

        private static String regionToken(String token) {
            if (REGION_ALIASES.containsKey(token)) {
                return token;
            }
            for (String suffix : List.of("지역에서", "지역에", "지역", "소재", "에서", "에는", "에")) {
                if (token.endsWith(suffix) && token.length() > suffix.length()) {
                    String candidate = token.substring(0, token.length() - suffix.length());
                    if (REGION_ALIASES.containsKey(candidate)) {
                        return candidate;
                    }
                }
            }
            return token;
        }
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private record IndexedProgram(
            SupportProgram program,
            String title,
            String categories,
            String regions,
            String target,
            String summary,
            String organization,
            String hashtags,
            String searchable,
            String sortTimestamp
    ) {
    }

    private record ScoredProgram(
            IndexedProgram candidate,
            int score,
            List<QueryTerm> matches
    ) {
    }

    private record CatalogCache(List<IndexedProgram> programs, Instant fetchedAt) {
    }
}
