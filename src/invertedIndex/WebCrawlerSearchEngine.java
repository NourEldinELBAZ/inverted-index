package invertedIndex;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;

public class WebCrawlerSearchEngine {

    
    private static final String SEED_URL = "https://en.wikipedia.org/wiki/List_of_pharaohs";
    private static final int MAX_PAGES = 10;  
    private static final int TOP_K = 10;   

   
    private final Map<Integer, String> docUrls = new LinkedHashMap<>();

    private final Map<Integer, String> docTexts = new LinkedHashMap<>();

    private final Map<String, Map<Integer, Integer>> invertedIndex = new HashMap<>();
    private final Map<String, Double> idf = new HashMap<>();
    private final Map<Integer, Map<String, Double>> docVectors = new HashMap<>();
    private final Map<Integer, Double> docLengths = new HashMap<>();


    public void crawl() {
        Queue<String> frontier = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Set<String> seen = new HashSet<>(); 

        frontier.add(SEED_URL);
        seen.add(SEED_URL);
        int docId = 0;

        System.out.println("=== Starting Web Crawler ===");
        System.out.println("Seed URL : " + SEED_URL);
        System.out.println("Max pages: " + MAX_PAGES);
        System.out.println();

        while (!frontier.isEmpty() && docId < MAX_PAGES) {
            String url = frontier.poll();

            if (visited.contains(url)) continue;
            if (!url.startsWith("https://en.wikipedia.org/wiki/")) continue;
            String wikiPath = url.substring("https://en.wikipedia.org/wiki/".length());
            if (wikiPath.contains(":")) continue;

            visited.add(url);

            try {
                Document html = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(5000)
                        .get();

                String text = html.select("div.mw-parser-output p").text();
                if (text.isEmpty()) text = html.body().text();

                docUrls.put(docId, url);
                docTexts.put(docId, text);
                System.out.println("[" + docId + "] Crawled: " + url);

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
                Thread.sleep(300); 

            } catch (IOException | InterruptedException e) {
                System.out.println("  [SKIP] Could not fetch: " + url + " — " + e.getMessage());
            }
        }

        System.out.println("\nCrawling done. Pages collected: " + docUrls.size());
    }


    public void buildInvertedIndex() {
        System.out.println("\n=== Building Inverted Index ===");

        for (Map.Entry<Integer, String> entry : docTexts.entrySet()) {
            int    docId = entry.getKey();
            String text  = entry.getValue();

            String[] tokens = text.toLowerCase().split("[^a-z]+");

            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens) {
                if (token.isEmpty() || isStopWord(token)) continue;
                termFreq.merge(token, 1, Integer::sum);
            }

            for (Map.Entry<String, Integer> tf : termFreq.entrySet()) {
                invertedIndex
                        .computeIfAbsent(tf.getKey(), k -> new HashMap<>())
                        .put(docId, tf.getValue());
            }
        }

        System.out.println("Unique terms indexed: " + invertedIndex.size());
    }


    public void computeIDF() {
        int N = docUrls.size();
        for (Map.Entry<String, Map<Integer, Integer>> entry : invertedIndex.entrySet()) {
            String term = entry.getKey();
            int    df   = entry.getValue().size();         
            double idfVal = Math.log((double) N / df);     
            idf.put(term, idfVal);
        }
        System.out.println("\n=== IDF computed for " + idf.size() + " terms ===");
    }


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


    private Map<String, Double> buildQueryVector(String query) {
        String[] tokens = query.toLowerCase().split("[^a-z]+");
        Map<String, Integer> queryTF = new HashMap<>();
        for (String token : tokens) {
            if (token.isEmpty() || isStopWord(token)) continue;
            queryTF.merge(token, 1, Integer::sum);
        }

        Map<String, Double> queryVec = new HashMap<>();
        for (Map.Entry<String, Integer> e : queryTF.entrySet()) {
            String term   = e.getKey();
            double idfVal = idf.getOrDefault(term, 0.0);
            queryVec.put(term, e.getValue() * idfVal);
        }
        return queryVec;
    }


    private Map<Integer, Double> computeCosineSimilarity(Map<String, Double> queryVec) {

        double queryLen = 0.0;
        for (double w : queryVec.values()) queryLen += w * w;
        queryLen = Math.sqrt(queryLen);

        Map<Integer, Double> scores = new HashMap<>();

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

        for (int docId : scores.keySet()) {
            double docLen = docLengths.getOrDefault(docId, 1.0);
            double denom  =  docLen; // *queryLen;  
            scores.put(docId, denom > 0 ? scores.get(docId) / denom : 0.0);
        }

        return scores;
    }


    private List<Map.Entry<Integer, Double>> rankTopK(Map<Integer, Double> scores, int k) {
        List<Map.Entry<Integer, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // descending
        return ranked.subList(0, Math.min(k, ranked.size()));
    }


    public void search(String query) {
        System.out.println("\n======================================");
        System.out.println("Query: \"" + query + "\"");
        System.out.println("======================================");

        Map<String, Double> queryVec = buildQueryVector(query);
        if (queryVec.isEmpty()) {
            System.out.println("No indexable terms found in query.");
            return;
        }
        System.out.println("Query terms (with TF-IDF weights):");
        queryVec.forEach((t, w) -> System.out.printf("  %-20s %.6f%n", t, w));

        Map<Integer, Double> scores = computeCosineSimilarity(queryVec);

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


    public static void main(String[] args) {
        WebCrawlerSearchEngine engine = new WebCrawlerSearchEngine();

        engine.crawl();
        engine.buildInvertedIndex();
        engine.computeIDF();
        engine.computeDocVectors();
        Scanner sc = new Scanner(System.in);
        System.out.print("\nEnter your query (words separated by spaces): ");
        String query = sc.nextLine();

        engine.search(query);

    }
}