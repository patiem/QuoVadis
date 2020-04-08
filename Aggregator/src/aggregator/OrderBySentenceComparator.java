/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package aggregator;

import java.util.Comparator;
import org.w3c.dom.Document;

/**
 *
 * @author paul
 */
public class OrderBySentenceComparator implements Comparator<Document> {
    @Override
    public int compare(Document d1, Document d2) {
        int id1 = Integer.parseInt(d1.getElementsByTagName("S").item(0).getAttributes().getNamedItem("id").getNodeValue());
        int id2 = Integer.parseInt(d2.getElementsByTagName("S").item(0).getAttributes().getNamedItem("id").getNodeValue());
        return id1 - id2;
    }
}
