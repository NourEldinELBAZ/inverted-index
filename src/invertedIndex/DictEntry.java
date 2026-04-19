/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package invertedIndex;

/**
 *
 * @author ehab
 */
public class DictEntry {

    public int doc_freq = 0; // number of documents that contain the term
    public int term_freq = 0; //number of times the term is mentioned in the collection
//=====================================================================
    //public HashSet<Integer> postingList;
    Posting pList = null;
    Posting last = null;
//------------------------------------------------

    boolean postingListContains(int i) {
        boolean found = false;
        Posting p = pList;
        while (p != null) {
            if (p.docId == i) {
                return true;
            }
            p = p.next;
        }
        return found;
    }
//------------------------------------------------

    int getPosting(int i) {
        int found = 0;
        Posting p = pList;
        while (p != null) {
            if (p.docId >= i) {
                if (p.docId == i) {
                    return p.dtf;
                } else {
                    return 0;
                }
            }
            p = p.next;
        }
        return found;
    }
//------------------------------------------------

    void addPosting(int i) {
        // pList = new Posting(i);
        if (pList == null) {
            pList = new Posting(i);
            last = pList;
        } else {
            last.next = new Posting(i);
            last = last.next;
        }
    }
//------------------------------------------------

// implement insert (int docId) method
    void insert(int docId) {
        // list is empty
        if (pList == null) {
            pList = new Posting(docId);
            last = pList;
            return;
        }

        // new docId is smaller than the head
        if (docId < pList.docId) {
            Posting newPost = new Posting(docId);
            newPost.next = pList;
            pList = newPost;
            return;
        }

        // find correct position in the middle
        Posting p = pList;
        while (p.next != null && p.next.docId < docId) {
            p = p.next;
        }

        // insert after p
        Posting newPost = new Posting(docId);
        newPost.next = p.next;
        p.next = newPost;

        // update last if inserted at the end
        if (newPost.next == null) {
            last = newPost;
        }
    }
    
 
    DictEntry() {
        //  postingList = new HashSet<Integer>();
    }

    DictEntry(int df, int tf) {
        doc_freq = df; 
        term_freq = tf;
    }

}
