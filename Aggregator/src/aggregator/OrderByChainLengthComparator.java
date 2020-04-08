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
public class OrderByChainLengthComparator implements Comparator<String> {
    @Override
    public int compare(String d1, String d2) {
        return ReferentialChains.heads.get(d2).size() - 
                ReferentialChains.heads.get(d1).size();
    }
}
