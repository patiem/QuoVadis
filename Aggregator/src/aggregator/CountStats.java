/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package aggregator;

import java.util.ArrayList;
import java.util.TreeMap;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author paul
 */
public class CountStats {
    
    static String fileName = "count_stats.txt";
    
    static void writeCountStats(Document doc) {
        
        ArrayList<String> nodeNames = new ArrayList<>();
        TreeMap<String, Integer> cnt = new TreeMap<>();
                
        nodeNames.add("ENTITY");
        nodeNames.add("REFERENTIAL");
        nodeNames.add("AFFECT");
        nodeNames.add("KINSHIP");
        nodeNames.add("SOCIAL");
        
        for (String name : nodeNames) {
            
             NodeList nodes = doc.getElementsByTagName(name);
             System.out.printf("Counting %s :\n", name);
             
             for (int i = 0; i < nodes.getLength(); i++) {
                 if (i % 1000 == 0) {
                     System.out.printf("Done %5d / %5d\n", i, nodes.getLength());
                 }
                 
                 Node node = nodes.item(i);
                 String type = node.getAttributes().getNamedItem("TYPE").getNodeValue();
                 String pair = name + " " + type;
                 
                 if (!cnt.containsKey(pair)) {
                     cnt.put(pair, 1);
                 } else {
                     cnt.put(pair, cnt.get(pair) + 1);
                 }
             }
        }
        
        for (String key : cnt.keySet()) {
            System.out.printf("%7s %5d\n", key, cnt.get(key));
        }
    }
}
