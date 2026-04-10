package com.crosschecknews.api.util;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TF-IDF (Term Frequency - Inverse Document Frequency) 벡터라이저.
 *
 * <h3>전처리 파이프라인 (tokenize)</h3>
 * <ol>
 *   <li><b>소문자 변환</b></li>
 *   <li><b>숫자/퍼센트 정규화</b> — "25%" / "25 percent" → NUM_PCT, 연도 → NUM_YEAR, 그 외 → NUM</li>
 *   <li><b>비단어 문자 제거</b> — 언더스코어는 복합어/정규화 토큰 보존을 위해 유지</li>
 *   <li><b>분리 + 최소 길이 필터</b></li>
 *   <li><b>복합어 바이그램 병합</b> — ["trade","war"] → ["trade_war"]</li>
 *   <li><b>alias 치환</b> — trump → donald_trump, us → united_states, beijing → china 등</li>
 *   <li><b>규칙 기반 stemming</b> — tariffs→tariff, imposing/imposed→impose 등</li>
 * </ol>
 *
 * <h3>TF-IDF 계산</h3>
 * <ol>
 *   <li>TF = 단어 빈도 / 문서 내 전체 토큰 수</li>
 *   <li>IDF = log((N+1)/(df+1)) + 1 (스무딩)</li>
 *   <li>Cosine Similarity로 같은 이슈 여부 판별</li>
 * </ol>
 *
 * <p>외부 라이브러리 없이 순수 Java로 구현해 포트폴리오에서 직접 설명 가능한 구조.
 */
public final class TfIdfVectorizer {

    /** 언더스코어(복합어·정규화 토큰용)는 보존, 나머지 비단어 문자는 공백 치환 */
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{Digit}_]");
    private static final int MIN_TOKEN_LENGTH = 2;

    // ── 기호/유니코드 정규화 ───────────────────────────────────────────────────
    /** 유니코드 발음구별부호(combining marks) — NFKD 분해 후 제거 */
    private static final Pattern DIACRITIC = Pattern.compile("\\p{M}");
    /** 약어 내부 점: "U.S." → "US", "G.D.P." → "GDP" */
    private static final Pattern ABBREV_DOT = Pattern.compile("(?<=\\p{L})\\.(?=\\p{L})");
    /** 영어 소유격: "China's", "companies'" → 's / ' 제거 */
    private static final Pattern POSSESSIVE = Pattern.compile("[\\u2019']s\\b|(?<=[sS])[\\u2019'](?=\\s|$)");

    // ── 숫자/퍼센트 정규화 ─────────────────────────────────────────────────────
    /**
     * "25%", "25 percent", "25 percentage points" → NUM_PCT
     * 토크나이징 전 전체 텍스트에 적용.
     */
    private static final Pattern PERCENT_PATTERN =
            Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:%|percent(?:age(?:\\s+points?)?)?)");
    /** 1900–2099 연도 → NUM_YEAR */
    private static final Pattern YEAR_PATTERN =
            Pattern.compile("\\b(?:19|20)\\d{2}\\b");
    /** 나머지 숫자열 → NUM */
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("\\b\\d+(?:[,.]\\d+)*\\b");

    // ── 고유명사 alias 사전 ────────────────────────────────────────────────────
    /**
     * 표면형(소문자) → 정규화 토큰.
     * 단일 alias는 alias 이후 stemming을 건너뛴다.
     *
     * <p>beijing → china 는 무역/관세 맥락에서는 중국 정부를 지칭하므로 포함.
     * 일반 지명 문맥에서는 오탐 가능 — 포트폴리오 도메인(무역 뉴스) 기준.
     */
    private static final Map<String, String> ALIAS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("trump",     "donald_trump");
        m.put("us",        "united_states");
        m.put("usa",       "united_states");
        m.put("america",   "united_states");
        m.put("american",  "united_states");
        m.put("americans", "united_states");
        m.put("beijing",   "china");
        m.put("chinese",   "china");
        m.put("uk",        "united_kingdom");
        m.put("britain",   "united_kingdom");
        m.put("british",   "united_kingdom");
        m.put("eu",        "european_union");
        m.put("europe",    "european_union");
        m.put("european",  "european_union");
        ALIAS = Collections.unmodifiableMap(m);
    }

    // ── 한국어 → canonical 토큰 사전 ──────────────────────────────────────────
    /**
     * 한국어 표면형(조사 제거 후) → 영어 canonical 토큰.
     * 영어 기사에서 동일 이슈를 표현하는 토큰과 일치하도록 정의한다.
     *
     * <p>수록 기준: 실제 수집 기사(FOX/NYT/조선/한겨레)에서 공통 이슈로 확인된 어휘만 포함.
     * 창작 금지 — API 응답 데이터 기반으로만 추가.
     */
    private static final Map<String, String> KOREAN_ALIAS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        // 인물
        m.put("트럼프",   "donald_trump");
        m.put("네타냐후", "netanyahu");
        m.put("푸틴",     "putin");
        m.put("밴스",     "vance");
        m.put("젤렌스키", "zelensky");
        m.put("시진핑",   "xi_jinping");
        m.put("왕이",     "wang_yi");
        m.put("하메네이", "khamenei");
        m.put("모즈타바", "mojtaba");
        // 국가·지역
        m.put("이란",     "iran");
        m.put("레바논",   "lebanon");
        m.put("이스라엘", "israel");
        m.put("나토",     "nato");
        m.put("우크라이나", "ukraine");
        m.put("파키스탄", "pakistan");
        m.put("북한",     "north_korea");
        m.put("대만",     "taiwan");
        m.put("러시아",   "russia");
        m.put("오만",     "oman");
        m.put("헝가리",   "hungary");
        // 조직·세력
        m.put("헤즈볼라", "hezbollah");
        m.put("백악관",   "white_house");
        m.put("연준",     "federal_reserve");
        // 핵심 어휘
        m.put("휴전",   "cease_fire");
        m.put("호르무즈", "hormuz");
        m.put("통행료", "toll");
        m.put("해협",   "strait");
        m.put("우라늄", "uranium");
        m.put("핵",     "nuclear");
        m.put("공습",   "airstrike");
        m.put("폭격",   "airstrike");
        m.put("협상",   "negotiation");
        m.put("제재",   "sanction");
        m.put("증시",   "stock_market");
        m.put("관세",   "tariff");
        KOREAN_ALIAS = Collections.unmodifiableMap(m);
    }

    // ── 한국어 조사 목록 ───────────────────────────────────────────────────────
    /**
     * 긴 조사부터 시도해야 짧은 조사에 오매칭을 방지할 수 있다.
     * 예: "에서" 를 "에" 보다 먼저 시도.
     */
    private static final String[] KOREAN_PARTICLES = {
            "에서", "으로", "까지", "부터", "에게", "한테", "께서",
            "이라", "라는", "이란", "라며", "이며",
            "이고", "라고",
            "이가", "이는", "이를", "이의",
            "이나", "라도",
            "으로", "로서",
            "에서", "에서의",
            "이서",
            "은", "는", "이", "가", "을", "를", "의", "에", "로", "와", "과", "도", "만", "서"
    };

    // ── 복합어 바이그램 목록 ───────────────────────────────────────────────────
    /**
     * 인접한 두 토큰(소문자 원형)이 이 목록에 해당하면 underscore 로 병합한다.
     * 바이그램 병합은 alias/stemming 이전에 수행하므로 원형 기준으로 정의한다.
     */
    private static final Set<String> BIGRAMS;
    static {
        Set<String> s = new LinkedHashSet<>();
        s.add("trade_war");
        s.add("trade_deal");
        s.add("trade_deficit");
        s.add("trade_policy");
        s.add("interest_rate");
        s.add("interest_rates");
        s.add("stock_market");
        s.add("white_house");
        s.add("supply_chain");
        s.add("south_korea");
        s.add("north_korea");
        s.add("federal_reserve");
        s.add("wall_street");
        s.add("cease_fire");
        BIGRAMS = Collections.unmodifiableSet(s);
    }

    // ── 강한 앵커 토큰 ────────────────────────────────────────────────────────
    /**
     * 특정 사건/행위자/대상을 좁혀주는 강한 앵커 토큰 집합.
     *
     * <p>선정 기준: 이 토큰을 공유한다는 것 자체가 같은 이슈일 가능성을 높여주는 토큰.
     * 광범위하게 등장하는 토큰(donald_trump, united_states, nato 등)은 제외.
     *
     * <p>인물: 특정 인물 (트럼프 제외 — 미국 뉴스 대부분에 등장)
     * <p>국가·세력: 현재 이슈의 핵심 행위자
     * <p>특정 대상: 특정 이슈에만 등장하는 지명/물질
     */
    public static final Set<String> STRONG_CANONICAL = Set.of(
            // 인물
            "netanyahu", "putin", "zelensky", "xi_jinping", "wang_yi", "khamenei", "mojtaba", "vance",
            // 국가·세력
            "iran", "israel", "lebanon", "ukraine", "north_korea", "hezbollah",
            "pakistan", "russia", "taiwan", "oman", "hungary",
            // 특정 대상
            "hormuz", "uranium"
    );

    // ── stemming 후 보정 사전 ─────────────────────────────────────────────────
    /**
     * suffix 제거 시 어미의 'e'가 함께 탈락하는 경우를 복원한다.
     * 예: "imposed"/"imposing" → stem → "impos" → 보정 → "impose"
     */
    private static final Map<String, String> POST_STEM = Map.of(
            "impos", "impose"
    );

    private TfIdfVectorizer() {}

    // ── 공개 API ──────────────────────────────────────────────────────────────

    /**
     * 텍스트에서 강한 앵커 토큰만 추출해 반환한다.
     * union 조건의 보조 지표로 사용한다.
     */
    public static Set<String> strongCanonicalTokens(String text) {
        Set<String> result = new HashSet<>();
        for (String token : tokenize(text)) {
            if (STRONG_CANONICAL.contains(token)) result.add(token);
        }
        return result;
    }

    /**
     * 문서(headline) 목록을 TF-IDF 벡터 배열로 변환한다.
     *
     * @param docs headline 문자열 목록 (인덱스 순서 보존)
     * @return docs[i]에 대응하는 TF-IDF 벡터 배열.
     *         모든 벡터는 동일 차원(어휘 크기). 빈 headline은 영벡터.
     */
    public static double[][] vectorize(List<String> docs) {
        int n = docs.size();

        List<List<String>> tokenizedDocs = docs.stream()
                .map(TfIdfVectorizer::tokenize)
                .toList();

        Set<String> vocabSet = new LinkedHashSet<>();
        for (List<String> tokens : tokenizedDocs) vocabSet.addAll(tokens);

        List<String> vocab = new ArrayList<>(vocabSet);
        Map<String, Integer> vocabIndex = new HashMap<>(vocab.size() * 2);
        for (int i = 0; i < vocab.size(); i++) vocabIndex.put(vocab.get(i), i);

        int V = vocab.size();
        if (V == 0) return new double[n][0];

        int[] df = new int[V];
        for (List<String> tokens : tokenizedDocs) {
            Set<String> seen = new HashSet<>(tokens);
            for (String term : seen) {
                Integer idx = vocabIndex.get(term);
                if (idx != null) df[idx]++;
            }
        }

        double[] idf = new double[V];
        for (int i = 0; i < V; i++) {
            idf[i] = Math.log((double)(n + 1) / (df[i] + 1)) + 1.0;
        }

        double[][] result = new double[n][V];
        for (int d = 0; d < n; d++) {
            List<String> tokens = tokenizedDocs.get(d);
            int total = tokens.size();
            if (total == 0) continue;

            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokens) counts.merge(token, 1, Integer::sum);

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                Integer idx = vocabIndex.get(entry.getKey());
                if (idx == null) continue;
                double tf = (double) entry.getValue() / total;
                result[d][idx] = tf * idf[idx];
            }
        }

        return result;
    }

    /**
     * headline을 전처리된 토큰 목록으로 변환한다.
     *
     * <p>전처리 순서:
     * <ol>
     *   <li>소문자 → 숫자 정규화 → 비단어 제거</li>
     *   <li>분리 + 최소 길이 필터</li>
     *   <li>복합어 바이그램 병합 (원형 기준)</li>
     *   <li>각 토큰에 alias 치환 또는 stemming 적용</li>
     * </ol>
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();

        String s = normalizeText(text.toLowerCase());
        s = normalizeNumbers(s);
        s = NON_WORD.matcher(s).replaceAll(" ");

        List<String> raw = Arrays.stream(s.split("\\s+"))
                .filter(t -> t.length() >= MIN_TOKEN_LENGTH)
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> merged = mergeBigrams(raw);

        List<String> result = new ArrayList<>(merged.size());
        for (String token : merged) {
            if (token.contains("_")) {
                // 복합어(trade_war) 또는 정규화 토큰(NUM_PCT) — 그대로 보존
                result.add(token);
            } else if (isKorean(token)) {
                // 한글 토큰: 조사 제거 후 KOREAN_ALIAS 조회, 없으면 조사만 제거한 형태 유지
                String stripped = stripKoreanParticle(token);
                String canonical = KOREAN_ALIAS.get(stripped);
                result.add(canonical != null ? canonical : stripped);
            } else {
                String aliased = ALIAS.get(token);
                if (aliased != null) {
                    result.add(aliased);
                } else {
                    String stemmed = stem(token);
                    result.add(POST_STEM.getOrDefault(stemmed, stemmed));
                }
            }
        }
        return result;
    }

    // ── 내부 전처리 메서드 ────────────────────────────────────────────────────

    /**
     * 기호/유니코드를 정규화한다 (소문자화 이후, 숫자 정규화 이전에 적용).
     * <ul>
     *   <li>NFKD 정규화 후 발음구별부호 제거: "café" → "cafe"</li>
     *   <li>약어 내부 점 제거: "U.S." → "US" (alias → united_states)</li>
     *   <li>영어 소유격 제거: "china's" → "china", "companies'" → "companies"</li>
     * </ul>
     *
     * <p>하이픈·슬래시·가운데점은 NON_WORD 단계에서 공백 처리 — 분리가 올바른 동작.
     * 한국어 조사 제거는 형태소 분석기 없이 정확도를 보장할 수 없어 미지원.
     */
    static String normalizeText(String text) {
        // 1. 발음구별부호 제거 (café → cafe, résumé → resume)
        text = DIACRITIC.matcher(Normalizer.normalize(text, Normalizer.Form.NFKD)).replaceAll("");
        // 2. 약어 점 제거 (u.s. → us, g.d.p. → gdp)
        text = ABBREV_DOT.matcher(text).replaceAll("");
        // 3. 소유격 제거 (china's → china, companies' → companies)
        text = POSSESSIVE.matcher(text).replaceAll("");
        return text;
    }

    /**
     * 숫자 표현을 정규화 토큰으로 치환한다 (토크나이징 이전 전체 텍스트에 적용).
     * <ul>
     *   <li>"25%" / "25 percent" / "25 percentage points" → NUM_PCT</li>
     *   <li>1900–2099 연도 → NUM_YEAR</li>
     *   <li>나머지 숫자열 → NUM</li>
     * </ul>
     */
    static String normalizeNumbers(String text) {
        text = PERCENT_PATTERN.matcher(text).replaceAll("NUM_PCT");
        text = YEAR_PATTERN.matcher(text).replaceAll("NUM_YEAR");
        text = NUMBER_PATTERN.matcher(text).replaceAll("NUM");
        return text;
    }

    /**
     * 규칙 기반 suffix 제거 stemmer.
     * 어근이 실제 영단어일 필요는 없고, 같은 어근끼리 동일 토큰이 되면 충분하다.
     *
     * <ul>
     *   <li>tariffs → tariff (-s)</li>
     *   <li>sanctions → sanction (-s)</li>
     *   <li>imposed / imposing → impos (-ed/-ing, POST_STEM 보정 후 impose)</li>
     *   <li>negotiations → negotiat (-ations)</li>
     *   <li>running → run (-ing + 중복자음 제거)</li>
     * </ul>
     */
    static String stem(String word) {
        int len = word.length();
        if (len > 7 && word.endsWith("ations")) return word.substring(0, len - 6);
        if (len > 5 && word.endsWith("ings"))   return removeSuffix(word, 4);
        if (len > 4 && word.endsWith("ing"))    return removeSuffix(word, 3);
        if (len > 4 && word.endsWith("ed"))     return removeSuffix(word, 2);
        if (len > 4 && word.endsWith("es") && !word.endsWith("ss")) return word.substring(0, len - 2);
        if (len > 3 && word.endsWith("s")  && !word.endsWith("ss")) return word.substring(0, len - 1);
        return word;
    }

    /**
     * suffix 를 제거한 후 중복 어말 자음을 하나 줄인다 (running → runn → run).
     * 결과가 MIN_TOKEN_LENGTH 미만이면 원래 단어를 반환한다.
     */
    private static String removeSuffix(String word, int suffixLen) {
        String base = word.substring(0, word.length() - suffixLen);
        if (base.length() >= 2) {
            char last = base.charAt(base.length() - 1);
            char prev = base.charAt(base.length() - 2);
            if (last == prev && isConsonant(last)) {
                base = base.substring(0, base.length() - 1);
            }
        }
        return base.length() >= MIN_TOKEN_LENGTH ? base : word;
    }

    private static boolean isConsonant(char c) {
        return "bcdfghjklmnpqrstvwxyz".indexOf(c) >= 0;
    }

    // ── 한국어 처리 ────────────────────────────────────────────────────────────

    /**
     * 토큰에 한글 문자가 하나 이상 포함되면 한국어 토큰으로 판단한다.
     *
     * <p>normalizeText()의 NFKD 정규화로 인해 한글 음절(가-힣, AC00-D7A3)이
     * 자모(ㄱ-ㅣ, 1100-11FF / 3130-318F)로 분해될 수 있다.
     * 두 범위를 모두 체크해야 NFKD 변환 후에도 감지된다.
     */
    static boolean isKorean(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7A3') return true; // 한글 음절 (NFC)
            if (c >= '\u1100' && c <= '\u11FF') return true; // 한글 자모 (NFKD 분해 후)
            if (c >= '\u3130' && c <= '\u318F') return true; // 한글 호환 자모
        }
        return false;
    }

    /**
     * 한글 토큰 끝에서 조사를 제거한다.
     *
     * <p>NFKD로 분해된 자모 형태일 수 있으므로 NFC로 재조합한 뒤 사전 조회한다.
     * 예: "이란에서" (NFKD 자모) → NFC → "이란에서" → strip "에서" → "이란"
     */
    static String stripKoreanParticle(String token) {
        // NFKD 분해 상태일 수 있으므로 NFC로 재조합
        String nfc = Normalizer.normalize(token, Normalizer.Form.NFC);
        for (String particle : KOREAN_PARTICLES) {
            if (nfc.endsWith(particle) && nfc.length() > particle.length()) {
                return nfc.substring(0, nfc.length() - particle.length());
            }
        }
        return nfc;
    }

    /**
     * 인접한 두 토큰이 BIGRAMS 목록에 있으면 underscore 로 병합한다.
     * 예: ["trade", "war"] → ["trade_war"]
     *
     * <p>원형(소문자) 기준으로 판단하므로 alias/stemming 이전에 호출해야 한다.
     * 3개 이상 연속어 병합은 미지원.
     */
    private static List<String> mergeBigrams(List<String> tokens) {
        if (tokens.size() < 2) return tokens;
        List<String> result = new ArrayList<>(tokens.size());
        int i = 0;
        while (i < tokens.size()) {
            if (i + 1 < tokens.size()) {
                String candidate = tokens.get(i) + "_" + tokens.get(i + 1);
                if (BIGRAMS.contains(candidate)) {
                    result.add(candidate);
                    i += 2;
                    continue;
                }
            }
            result.add(tokens.get(i));
            i++;
        }
        return result;
    }
}
