package invertedIndex;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;

/**
 * HW#2 – Web Crawler + Inverted Index + TF-IDF + Cosine Similarity
 *
 * Extends HW#1 (inverted index on local files) with:
 *   1. BFS Web Crawler  – starts from the pharaohs seed URL, visits up to N=10 pages
 *   2. Inverted Index   – term → { docId → TF }
 *   3. TF-IDF Weighting – idf[term] = log(N / DF)
 *   4. Document Vectors – doc_vectors[docId][term] = TF * IDF
 *   5. Query Vector     – same TF-IDF weighting applied to query terms
 *   6. Cosine Similarity– dot-product / (|query| * |doc|)
 *   7. Length Norm      – scores[doc] = scores[doc] / length[doc]
 *   8. Top-K Ranking    – returns the top K=10 most similar documents
 *
 * DEPENDENCY: Jsoup (HTML parser)
 *   Add to pom.xml / build.gradle:
 *     <dependency>
 *       <groupId>org.jsoup</groupId>
 *       <artifactId>jsoup</artifactId>
 *       <version>1.17.2</version>
 *     </dependency>
 *
 * Or download the jar from https://jsoup.org/download and add it to your classpath.
 */
public class WebCrawlerSearchEngine {

    // -----------------------------------------------------------------------
    // Configuration constants
    // -----------------------------------------------------------------------
    private static final String SEED_URL = "https://en.wikipedia.org/wiki/List_of_pharaohs";
    private static final int    MAX_PAGES = 10;   // N
    private static final int    TOP_K     = 10;   // k for ranking

    // -----------------------------------------------------------------------
    // Data structures
    // -----------------------------------------------------------------------

    /** docId → URL */
    private final Map<Integer, String>              docUrls      = new LinkedHashMap<>();

    /** docId → raw text of the page */
    private final Map<Integer, String>              docTexts     = new LinkedHashMap<>();

    /** term → { docId → TF (raw count) } */
    private final Map<String, Map<Integer, Integer>> invertedIndex = new HashMap<>();

    /** term → IDF */
    private final Map<String, Double>               idf          = new HashMap<>();

    /** docId → { term → TF-IDF weight } */
    private final Map<Integer, Map<String, Double>> docVectors   = new HashMap<>();

    /** docId → Euclidean length of its TF-IDF vector */
    private final Map<Integer, Double>              docLengths   = new HashMap<>();

    // -----------------------------------------------------------------------
    // 1.  WEB CRAWLER
    // -----------------------------------------------------------------------

    /**
     * BFS crawler starting from seedUrl.
     * Stops when MAX_PAGES unique Wikipedia pages have been visited.
     */
    public void crawl() {
        Queue<String> frontier = new LinkedList<>();
        Set<String>   visited  = new HashSet<>();
        Set<String>   seen     = new HashSet<>(); // prevents duplicate URLs in queue

        frontier.add(SEED_URL);
        seen.add(SEED_URL);
        int docId = 0;

        System.out.println("=== Starting Web Crawler ===");
        System.out.println("Seed URL : " + SEED_URL);
        System.out.println("Max pages: " + MAX_PAGES);
        System.out.println();

        while (!frontier.isEmpty() && docId < MAX_PAGES) {
            String url = frontier.poll();

            // Skip already-visited or non-Wikipedia pages
            if (visited.contains(url)) continue;
            if (!url.startsWith("https://en.wikipedia.org/wiki/")) continue;
            // Skip special Wikipedia namespaces (e.g. File:, Talk:, Help:)
            // Only check the part AFTER /wiki/
            String wikiPath = url.substring("https://en.wikipedia.org/wiki/".length());
            if (wikiPath.contains(":")) continue;

            visited.add(url);

            try {
                // Fetch the page (timeout = 5 seconds)
                Document html = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(5000)
                        .get();

                // Extract visible text (from the main content div)
                String text = html.select("div.mw-parser-output p").text();
                if (text.isEmpty()) text = html.body().text();

                // Store
                docUrls.put(docId, url);
                docTexts.put(docId, text);
                System.out.println("[" + docId + "] Crawled: " + url);

                // Find outgoing Wikipedia links and add to frontier
                Elements links = html.select("a[href]");
                for (Element link : links) {
                    String href = link.attr("abs:href");
                    String linkPath = href.startsWith("https://en.wikipedia.org/wiki/") ? href.substring("https://en.wikipedia.org/wiki/".length()) : "";
                    if (href.startsWith("https://en.wikipedia.org/wiki/")
                            && !href.contains("#")
                            && !linkPath.contains(":")
                            && !seen.contains(href)) {
                        frontier.add(href);
                        seen.add(href);
                    }
                }

                docId++;
                Thread.sleep(300); // polite delay

            } catch (IOException | InterruptedException e) {
                System.out.println("  [SKIP] Could not fetch: " + url + " — " + e.getMessage());
            }
        }

        System.out.println("\nCrawling done. Pages collected: " + docUrls.size());
    }

    // -----------------------------------------------------------------------
    // 2.  BUILD INVERTED INDEX  (term → { docId → TF })
    // -----------------------------------------------------------------------

    public void buildInvertedIndex() {
        System.out.println("\n=== Building Inverted Index ===");

        for (Map.Entry<Integer, String> entry : docTexts.entrySet()) {
            int    docId = entry.getKey();
            String text  = entry.getValue();

            // Tokenise: lowercase, letters only
            String[] tokens = text.toLowerCase().split("[^a-z]+");

            // Count term frequency within this document
            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens) {
                if (token.isEmpty() || isStopWord(token)) continue;
                termFreq.merge(token, 1, Integer::sum);
            }

            // Add to inverted index
            for (Map.Entry<String, Integer> tf : termFreq.entrySet()) {
                invertedIndex
                        .computeIfAbsent(tf.getKey(), k -> new HashMap<>())
                        .put(docId, tf.getValue());
            }
        }

        System.out.println("Unique terms indexed: " + invertedIndex.size());
    }

    // -----------------------------------------------------------------------
    // 3.  COMPUTE IDF
    //     idf[term] = log(N / DF)
    // -----------------------------------------------------------------------

    public void computeIDF() {
        int N = docUrls.size();
        for (Map.Entry<String, Map<Integer, Integer>> entry : invertedIndex.entrySet()) {
            String term = entry.getKey();
            int    df   = entry.getValue().size();          // number of docs containing term
            double idfVal = Math.log((double) N / df);     // natural log
            idf.put(term, idfVal);
        }
        System.out.println("\n=== IDF computed for " + idf.size() + " terms ===");
    }

    // -----------------------------------------------------------------------
    // 4.  COMPUTE TF-IDF DOCUMENT VECTORS  +  DOCUMENT LENGTHS
    //     weight = TF * IDF
    //     length[doc] = sqrt( sum of weight^2 )
    // -----------------------------------------------------------------------

    public void computeDocVectors() {
        System.out.println("\n=== Building Document Vectors (TF-IDF) ===");

        for (Map.Entry<String, Map<Integer, Integer>> entry : invertedIndex.entrySet()) {
            String term    = entry.getKey();
            double idfVal  = idf.getOrDefault(term, 0.0);

            for (Map.Entry<Integer, Integer> posting : entry.getValue().entrySet()) {
                int    docId  = posting.getKey();
                int    tf     = posting.getValue();
                double weight = tf * idfVal;

                docVectors
                        .computeIfAbsent(docId, k -> new HashMap<>())
                        .put(term, weight);
            }
        }

        // Compute Euclidean length of each document vector
        for (Map.Entry<Integer, Map<String, Double>> entry : docVectors.entrySet()) {
            int    docId  = entry.getKey();
            double sumSq  = 0.0;
            for (double w : entry.getValue().values()) {
                sumSq += w * w;
            }
            docLengths.put(docId, Math.sqrt(sumSq));
        }

        System.out.println("Document vectors built for " + docVectors.size() + " documents.");
    }

    // -----------------------------------------------------------------------
    // 5.  CONVERT QUERY → TF-IDF VECTOR
    // -----------------------------------------------------------------------

    private Map<String, Double> buildQueryVector(String query) {
        // Count raw TF of each query term
        String[] tokens = query.toLowerCase().split("[^a-z]+");
        Map<String, Integer> queryTF = new HashMap<>();
        for (String token : tokens) {
            if (token.isEmpty() || isStopWord(token)) continue;
            queryTF.merge(token, 1, Integer::sum);
        }

        // Multiply by IDF (only terms that appear in the index get a weight)
        Map<String, Double> queryVec = new HashMap<>();
        for (Map.Entry<String, Integer> e : queryTF.entrySet()) {
            String term   = e.getKey();
            double idfVal = idf.getOrDefault(term, 0.0);
            queryVec.put(term, e.getValue() * idfVal);
        }
        return queryVec;
    }

    // -----------------------------------------------------------------------
    // 6 & 7.  COSINE SIMILARITY  +  LENGTH NORMALISATION
    //
    //  Step 6: scores[doc] = dot(queryVec, docVec)    <- raw dot product
    //  Step 7: scores[doc] = scores[doc] / length[doc] <- divide by doc length
    //
    //  Final formula: dot(q, d) / |d|
    //  (Matches assignment hints exactly)
    // -----------------------------------------------------------------------

    private Map<Integer, Double> computeCosineSimilarity(Map<String, Double> queryVec) {

        // Compute query vector length |q|
        double queryLen = 0.0;
        for (double w : queryVec.values()) queryLen += w * w;
        queryLen = Math.sqrt(queryLen);

        Map<Integer, Double> scores = new HashMap<>();

        // Step 6: compute dot product  dot(q, d)
        for (Map.Entry<String, Double> qEntry : queryVec.entrySet()) {
            String term    = qEntry.getKey();
            double qWeight = qEntry.getValue();

            Map<Integer, Integer> postings = invertedIndex.get(term);
            if (postings == null) continue;

            for (int docId : postings.keySet()) {
                double dWeight = docVectors
                        .getOrDefault(docId, Collections.emptyMap())
                        .getOrDefault(term, 0.0);
                scores.merge(docId, qWeight * dWeight, Double::sum);
            }
        }

        // Step 7: normalize by doc length  →  scores[doc] = scores[doc] / length[doc]
        // Full cosine = dot(q,d) / (|q| * |d|)
        for (int docId : scores.keySet()) {
            double docLen = docLengths.getOrDefault(docId, 1.0);
            double denom  = queryLen * docLen;   // |q| * |d|
            scores.put(docId, denom > 0 ? scores.get(docId) / denom : 0.0);
        }

        return scores;
    }

    // -----------------------------------------------------------------------
    // 8.  RANK TOP-K DOCUMENTS
    // -----------------------------------------------------------------------

    private List<Map.Entry<Integer, Double>> rankTopK(Map<Integer, Double> scores, int k) {
        List<Map.Entry<Integer, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // descending
        return ranked.subList(0, Math.min(k, ranked.size()));
    }

    // -----------------------------------------------------------------------
    // MAIN SEARCH PIPELINE
    // -----------------------------------------------------------------------

    public void search(String query) {
        System.out.println("\n======================================");
        System.out.println("Query: \"" + query + "\"");
        System.out.println("======================================");

        // Step 5 – query vector
        Map<String, Double> queryVec = buildQueryVector(query);
        if (queryVec.isEmpty()) {
            System.out.println("No indexable terms found in query.");
            return;
        }
        System.out.println("Query terms (with TF-IDF weights):");
        queryVec.forEach((t, w) -> System.out.printf("  %-20s %.6f%n", t, w));

        // Steps 6 & 7 – cosine similarity + length normalisation
        Map<Integer, Double> scores = computeCosineSimilarity(queryVec);

        // Step 8 – top-K ranking
        List<Map.Entry<Integer, Double>> topK = rankTopK(scores, TOP_K);

        System.out.println("\nTop-" + TOP_K + " results:");
        System.out.println("-----------------------------------------");
        int rank = 1;
        for (Map.Entry<Integer, Double> entry : topK) {
            int    docId = entry.getKey();
            double score = entry.getValue();
            System.out.printf("%2d. [score=%.8f] %s%n", rank++, score, docUrls.get(docId));
        }
    }

    // -----------------------------------------------------------------------
    // UTILITY – tiny stop-word list
    // -----------------------------------------------------------------------

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a","an","the","and","or","but","in","on","at","to","for",
            "of","with","by","from","is","was","are","were","be","been",
            "has","have","had","do","does","did","will","would","could",
            "should","may","might","this","that","these","those","it",
            "its","as","if","not","no","so","up","out","about","into",
            "than","then","also","both","each","few","more","most","other",
            "some","such","only","own","same","too","very","just","over"
    ));

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }

    // -----------------------------------------------------------------------
    // ENTRY POINT
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        WebCrawlerSearchEngine engine = new WebCrawlerSearchEngine();

        // ── Phase 1: Crawl ──────────────────────────────────────────────────
        engine.crawl();

        // ── Phase 2: Build index ────────────────────────────────────────────
        engine.buildInvertedIndex();

        // ── Phase 3: Compute IDF ────────────────────────────────────────────
        engine.computeIDF();

        // ── Phase 4: Compute document TF-IDF vectors ────────────────────────
        engine.computeDocVectors();

        // ── Phase 5-8: Accept query and rank documents ───────────────────────
        Scanner sc = new Scanner(System.in);
        System.out.print("\nEnter your query (words separated by spaces): ");
        String query = sc.nextLine();

        engine.search(query);

        // You can call engine.search() again for multiple queries:
        // engine.search("ancient egypt dynasty");
        // engine.search("nile river flood");
    }
}