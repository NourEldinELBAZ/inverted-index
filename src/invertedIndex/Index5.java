/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package invertedIndex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.InputStreamReader;
import static java.lang.Math.log10;
import static java.lang.Math.sqrt;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.io.PrintWriter;

/**
 * Index5 - Inverted Index with Positional Information
 * Builds and searches an inverted index from a collection of text files.
 * Supports Boolean AND search and positional phrase search.
 *
 * @author ehab
 */
public class Index5 {

    //--------------------------------------------
    int N = 0;
    public Map<Integer, SourceRecord> sources;  // store the doc_id and the file name.

    public HashMap<String, DictEntry> index; // The inverted index
    //--------------------------------------------

    /**
     * Constructor - initializes the sources map and the inverted index map.
     */
    public Index5() {
        sources = new HashMap<Integer, SourceRecord>();
        index = new HashMap<String, DictEntry>();
    }

    /**
     * Sets the total number of documents in the collection.
     * @param n the number of documents
     */
    public void setN(int n) {
        N = n;
    }

    /**
     * Prints a posting list to the console.
     * Displays document IDs separated by commas, without a trailing comma.
     * Example output: [0,1,2,3]
     *
     * @param p the head of the posting list to print
     */
    public void printPostingList(Posting p) {
        System.out.print("[");
        while (p != null) {
            System.out.print(p.docId);
            if (p.next != null) {
                System.out.print(",");  // print comma only if it's not the last node
            }
            p = p.next;
        }
        System.out.println("]");
    }

    /**
     * Prints all terms in the inverted index along with their posting lists.
     * Also prints the total number of unique terms.
     */
    public void printDictionary() {
        Iterator it = index.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            DictEntry dd = (DictEntry) pair.getValue();
            System.out.print("** [" + pair.getKey() + "," + dd.doc_freq + "]       =--> ");
            printPostingList(dd.pList);
        }
        System.out.println("------------------------------------------------------");
        System.out.println("*** Number of terms = " + index.size());
    }

    /**
     * Builds the inverted index from an array of file paths.
     * Reads each file line by line, tokenizes the content,
     * removes stop words, and stores each term with its document ID
     * and positional information.
     *
     * @param files array of absolute file paths to index
     */
    public void buildIndex(String[] files) {
        int fid = 0;
        for (String fileName : files) {
            try (BufferedReader file = new BufferedReader(new FileReader(fileName))) {
                if (!sources.containsKey(fileName)) {
                    sources.put(fid, new SourceRecord(fid, fileName, fileName, "notext"));
                }
                String ln;
                int flen = 0;
                int position = 0;

                while ((ln = file.readLine()) != null) {
                    position = indexOneLine(ln, fid, position);
                }
                // Store total token count as document length
                sources.get(fid).length = position;

            } catch (IOException e) {
                System.out.println("File " + fileName + " not found. Skip it");
            }
            fid++;
        }
    }

    /**
     * Indexes a single line of text for a given document.
     * Splits the line into words, filters stop words, stems each word,
     * and adds it to the inverted index with its position.
     *
     * @param ln       the line of text to index
     * @param fid      the document ID this line belongs to
     * @param position the current word position counter in the document
     * @return the updated position counter after processing this line
     */
    public int indexOneLine(String ln, int fid, int position) {
        String[] words = ln.split("\\W+");

        for (int pos = 0; pos < words.length; pos++) {
            String word = words[pos].toLowerCase();

            if (stopWord(word)) continue;

            word = stemWord(word);

            if (!index.containsKey(word)) {
                index.put(word, new DictEntry());
            }

            if (!index.get(word).postingListContains(fid)) {
                index.get(word).doc_freq++;

                if (index.get(word).pList == null) {
                    index.get(word).pList = new Posting(fid);
                    index.get(word).last = index.get(word).pList;
                } else {
                    index.get(word).last.next = new Posting(fid);
                    index.get(word).last = index.get(word).last.next;
                }
            } else {
                index.get(word).last.dtf++;
            }

            // Store the position of this word occurrence for phrase search
            index.get(word).last.positions.add(position);

            index.get(word).term_freq++;

            position++;
        }

        return position;
    }

    /**
     * Checks whether a given word is a stop word.
     * Stop words are common words that are excluded from indexing
     * (e.g., "the", "and", "to").
     * Also filters out words shorter than 2 characters.
     *
     * @param word the word to check
     * @return true if the word is a stop word, false otherwise
     */
    boolean stopWord(String word) {
        if (word.equals("the") || word.equals("to") || word.equals("be") || word.equals("for")
                || word.equals("from") || word.equals("in") || word.equals("a")
                || word.equals("into") || word.equals("by") || word.equals("or")
                || word.equals("and") || word.equals("that")) {
            return true;
        }
        if (word.length() < 2) {
            return true;
        }
        return false;
    }

    /**
     * Applies stemming to a word to reduce it to its root form.
     * Currently disabled — returns the word unchanged.
     *
     * @param word the word to stem
     * @return the stemmed word (currently returns the original word)
     */
    String stemWord(String word) {
        return word;
//        Stemmer s = new Stemmer();
//        s.addString(word);
//        s.stem();
//        return s.toString();
    }

    /**
     * Computes the intersection of two posting lists (Boolean AND).
     * Uses the standard two-pointer merge algorithm:
     * advances through both lists simultaneously and collects
     * only the document IDs that appear in both lists.
     *
     * @param pL1 the first posting list
     * @param pL2 the second posting list
     * @return a new posting list containing only shared document IDs
     */
    Posting intersect(Posting pL1, Posting pL2) {
        //  INTERSECT ( p1 , p2 )
        //  1  answer ← {}
        Posting answer = null;
        Posting last = null;
        //  2 while p1 != NIL and p2 != NIL
        while (pL1 != null && pL2 != null) {
            //  3 do if docID(p1) = docID(p2)
            if (pL1.docId == pL2.docId) {
                //  4   then ADD(answer, docID(p1))
                Posting newPost = new Posting(pL1.docId);
                if (answer == null) {
                    answer = newPost;
                    last = answer;
                } else {
                    last.next = newPost;
                    last = last.next;
                }
                //  5       p1 ← next(p1)
                pL1 = pL1.next;
                //  6       p2 ← next(p2)
                pL2 = pL2.next;
            }
            //  7   else if docID(p1) < docID(p2)
            else if (pL1.docId < pL2.docId) {
                //  8        then p1 ← next(p1)
                pL1 = pL1.next;
            } else {
                //  9        else p2 ← next(p2)
                pL2 = pL2.next;
            }
        }
        //  10 return answer
        return answer;
    }

    /**
     * Computes the positional intersection of two posting lists.
     * Returns only documents where the two terms appear with
     * consecutive positions (pos2 - pos1 == 1), enabling phrase search.
     *
     * @param pL1 posting list of the first term
     * @param pL2 posting list of the second term
     * @param gap the required positional gap between the two terms
     * @return a posting list of documents where the terms appear consecutively
     */
    Posting positionalIntersect(Posting pL1, Posting pL2, int gap) {
        Posting answer = null;
        Posting last = null;

        while (pL1 != null && pL2 != null) {
            if (pL1.docId == pL2.docId) {
                boolean found = false;

                // Check if any position pair satisfies the consecutive constraint
                for (int pos1 : pL1.positions) {
                    for (int pos2 : pL2.positions) {
                        if (pos2 - pos1 == 1) {
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }

                if (found) {
                    Posting newPost = new Posting(pL1.docId);
                    if (answer == null) {
                        answer = newPost;
                        last = answer;
                    } else {
                        last.next = newPost;
                        last = last.next;
                    }
                }
                pL1 = pL1.next;
                pL2 = pL2.next;
            } else if (pL1.docId < pL2.docId) {
                pL1 = pL1.next;
            } else {
                pL2 = pL2.next;
            }
        }
        return answer;
    }

    /**
     * Performs a Boolean AND search for any number of query terms.
     * Retrieves the posting list for each term and intersects them all.
     * Returns a list of documents that contain ALL query terms.
     *
     * @param phrase the search query (one or more space-separated terms)
     * @return a formatted string listing matching document IDs, titles, and lengths
     */
    public String find_24_01(String phrase) {
        String result = "";
        String[] words = phrase.split("\\W+");
        int len = words.length;

        // Guard against empty or invalid input
        if (words.length == 0 || phrase.trim().isEmpty()) {
            return "Please enter a valid search phrase.";
        }

        // Check if the first word exists in the index
        if (!index.containsKey(words[0].toLowerCase())) {
            return "No results found for: " + words[0];
        }

        Posting posting = index.get(words[0].toLowerCase()).pList;
        int i = 1;

        // Intersect posting lists for all remaining words
        while (i < len) {
            String w = words[i].toLowerCase();
            if (!index.containsKey(w)) {
                return "No results found for: " + words[i];
            }
            posting = intersect(posting, index.get(w).pList);
            i++;
        }

        // Build result string from matching postings
        while (posting != null) {
            result += "\t" + posting.docId + " - " + sources.get(posting.docId).title
                    + " - " + sources.get(posting.docId).length + "\n";
            posting = posting.next;
        }
        return result;
    }

    /**
     * Performs a phrase search using positional information.
     * Returns only documents where the query terms appear
     * in the exact order and consecutively as typed.
     *
     * @param phrase the exact phrase to search for
     * @return a formatted string listing matching document IDs and titles
     */
    public String find_phrase(String phrase) {
        String result = "";
        String[] words = phrase.toLowerCase().split("\\W+");

        if (words.length == 0) return "Invalid phrase.";

        // Check all words exist in the index before searching
        for (String w : words) {
            if (!index.containsKey(w)) {
                return "No results for: " + w;
            }
        }

        Posting p = index.get(words[0]).pList;

        // For each document containing the first word
        while (p != null) {
            int docId = p.docId;
            boolean matchFound = false;

            // For each position of the first word in this document
            for (int pos : p.positions) {
                boolean fullMatch = true;

                // Check that each subsequent word appears at position+i
                for (int i = 1; i < words.length; i++) {
                    Posting nextPosting = index.get(words[i]).pList;

                    // Find the posting for this document
                    while (nextPosting != null && nextPosting.docId != docId) {
                        nextPosting = nextPosting.next;
                    }

                    if (nextPosting == null || !nextPosting.positions.contains(pos + i)) {
                        fullMatch = false;
                        break;
                    }
                }

                if (fullMatch) {
                    matchFound = true;
                    break;
                }
            }

            if (matchFound) {
                result += "\t" + docId + " - " + sources.get(docId).title + "\n";
            }

            p = p.next;
        }

        return result.isEmpty() ? "No documents found." : result;
    }

    /**
     * Sorts an array of strings alphabetically using bubble sort.
     * Used to sort file names before indexing to ensure consistent ordering.
     *
     * @param words the array of strings to sort
     * @return the sorted array
     */
    String[] sort(String[] words) {
        boolean sorted = false;
        String sTmp;
        while (!sorted) {
            sorted = true;
            for (int i = 0; i < words.length - 1; i++) {
                int compare = words[i].compareTo(words[i + 1]);
                if (compare > 0) {
                    sTmp = words[i];
                    words[i] = words[i + 1];
                    words[i + 1] = sTmp;
                    sorted = false;
                }
            }
        }
        return words;
    }

    /**
     * Saves the current index and source records to a file on disk.
     * The file is divided into two sections:
     * Section 1: source records (document metadata)
     * Section 2: index terms with their posting lists
     *
     * @param storageName the name of the file to write to
     */
    public void store(String storageName) {
        try {
            String pathToStorage = "C:/Users/إسراء/Downloads/inverted-index-main/tmp11/rl/" + storageName;
            Writer wr = new FileWriter(pathToStorage);
            for (Map.Entry<Integer, SourceRecord> entry : sources.entrySet()) {
                System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue().URL
                        + ", Value = " + entry.getValue().title + ", Value = " + entry.getValue().text);
                wr.write(entry.getKey().toString() + ",");
                wr.write(entry.getValue().URL.toString() + ",");
                wr.write(entry.getValue().title.replace(',', '~') + ",");
                wr.write(entry.getValue().length + ",");
                wr.write(String.format("%4.4f", entry.getValue().norm) + ",");
                wr.write(entry.getValue().text.toString().replace(',', '~') + "\n");
            }
            wr.write("section2" + "\n");

            Iterator it = index.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                DictEntry dd = (DictEntry) pair.getValue();
                wr.write(pair.getKey().toString() + "," + dd.doc_freq + "," + dd.term_freq + ";");
                Posting p = dd.pList;
                while (p != null) {
                    wr.write(p.docId + "," + p.dtf + ":");
                    p = p.next;
                }
                wr.write("\n");
            }
            wr.write("end" + "\n");
            wr.close();
            System.out.println("=============EBD STORE=============");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks whether a storage file already exists on disk.
     *
     * @param storageName the name of the storage file to check
     * @return true if the file exists and is not a directory, false otherwise
     */
    public boolean storageFileExists(String storageName) {
        java.io.File f = new java.io.File("/home/ehab/tmp11/rl/" + storageName);
        if (f.exists() && !f.isDirectory())
            return true;
        return false;
    }

    /**
     * Creates an empty storage file with just an "end" marker.
     * Used to initialize a new storage file before writing data.
     *
     * @param storageName the name of the storage file to create
     */
    public void createStore(String storageName) {
        try {
            String pathToStorage = "/home/ehab/tmp11/" + storageName;
            Writer wr = new FileWriter(pathToStorage);
            wr.write("end" + "\n");
            wr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads a previously saved index from disk into memory.
     * Reads source records and posting lists from the storage file
     * and reconstructs the index and sources maps.
     *
     * @param storageName the name of the storage file to load
     * @return the reconstructed inverted index as a HashMap
     */
    public HashMap<String, DictEntry> load(String storageName) {
        try {
            String pathToStorage = "/home/ehab/tmp11/rl/" + storageName;
            sources = new HashMap<Integer, SourceRecord>();
            index = new HashMap<String, DictEntry>();
            BufferedReader file = new BufferedReader(new FileReader(pathToStorage));
            String ln = "";
            int flen = 0;

            // Read source records until "section2" marker
            while ((ln = file.readLine()) != null) {
                if (ln.equalsIgnoreCase("section2")) {
                    break;
                }
                String[] ss = ln.split(",");
                int fid = Integer.parseInt(ss[0]);
                try {
                    System.out.println("**>>" + fid + " " + ss[1] + " " + ss[2].replace('~', ',')
                            + " " + ss[3] + " [" + ss[4] + "]   " + ss[5].replace('~', ','));
                    SourceRecord sr = new SourceRecord(fid, ss[1], ss[2].replace('~', ','),
                            Integer.parseInt(ss[3]), Double.parseDouble(ss[4]), ss[5].replace('~', ','));
                    sources.put(fid, sr);
                } catch (Exception e) {
                    System.out.println(fid + "  ERROR  " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Read index terms and posting lists until "end" marker
            while ((ln = file.readLine()) != null) {
                if (ln.equalsIgnoreCase("end")) {
                    break;
                }
                String[] ss1 = ln.split(";");
                String[] ss1a = ss1[0].split(",");
                String[] ss1b = ss1[1].split(":");
                index.put(ss1a[0], new DictEntry(Integer.parseInt(ss1a[1]), Integer.parseInt(ss1a[2])));
                String[] ss1bx;
                for (int i = 0; i < ss1b.length; i++) {
                    ss1bx = ss1b[i].split(",");
                    if (index.get(ss1a[0]).pList == null) {
                        index.get(ss1a[0]).pList = new Posting(Integer.parseInt(ss1bx[0]), Integer.parseInt(ss1bx[1]));
                        index.get(ss1a[0]).last = index.get(ss1a[0]).pList;
                    } else {
                        index.get(ss1a[0]).last.next = new Posting(Integer.parseInt(ss1bx[0]), Integer.parseInt(ss1bx[1]));
                        index.get(ss1a[0]).last = index.get(ss1a[0]).last.next;
                    }
                }
            }
            System.out.println("============= END LOAD =============");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return index;
    }
}

//=====================================================================
