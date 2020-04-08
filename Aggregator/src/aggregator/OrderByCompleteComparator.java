/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package aggregator;

import static aggregator.Aggregator.countNodesByName;
import java.util.ArrayList;
import java.util.Comparator;
import org.w3c.dom.Document;

/**
 *
 * @author paul
 */
public class OrderByCompleteComparator implements Comparator<Document> {
    @Override
    public int compare(Document d1, Document d2) {
        
        ArrayList<Document> docs = new ArrayList<>();
        docs.add(d1);
        docs.add(d2);
        double[] score = new double[2];
        for (int i = 0; i < 2; i++) {
            int sentences = countNodesByName(docs.get(i), "S");
            int E = countNodesByName(docs.get(i), "ENTITY");
            int R = countNodesByName(docs.get(i), "REFERENTIAL");
            int T = countNodesByName(docs.get(i), "TRIGGER");
            int A = countNodesByName(docs.get(i), "AFFECT");
            int K = countNodesByName(docs.get(i), "KINSHIP");
            int S = countNodesByName(docs.get(i), "SOCIAL");
            score[i] = (double)(E + 2 * R + 5 * (A + K + S)) / sentences;
        }

        if (score[0] > score[1]) {
            return -1;
        } else if (score[0] < score[1]) {
            return 1;
        }
        return 0;
    }
}
