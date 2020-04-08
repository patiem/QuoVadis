package corefgraph;

import static corefgraph.Graph.domDocument;
import java.util.HashMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WordsMapping {
    
    public static HashMap<String, String> entityToWords;
    
    public static String addWordsRec(Node cNode) {
        if (cNode.getNodeName().equals("W")) {
            return cNode.getTextContent();
        } else {
            String ret = "";
            for (int i = 0; i < cNode.getChildNodes().getLength(); i++) {
                ret += addWordsRec(cNode.getChildNodes().item(i)) + " ";
            }
            return ret;
        }
    }
    
    public static void buildEntityToWords() {
        entityToWords = new HashMap<>();
        NodeList eList = domDocument.getElementsByTagName("ENTITY");
        for (int i = 0; i < eList.getLength(); i++) {
            Node eNode = eList.item(i);
            String words = addWordsRec(eNode);
            words = words.trim().replaceAll("( )+", " ");
            entityToWords.put(eNode.getAttributes().getNamedItem("ID").getNodeValue(), words);
        }
    }
    
    public static HashMap<String, String> sentenceIdToWords;
    public static void buildSentenceToWordsMapping() {
        sentenceIdToWords = new HashMap<>();
        NodeList sList = domDocument.getElementsByTagName("S");
        for (int i = 0; i < sList.getLength(); i++) {
            Node sNode = sList.item(i);
            String id = sNode.getAttributes().getNamedItem("id").getNodeValue();
            String words = addWordsRec(sNode);
            words = words.trim().replaceAll("( )+", " ");
            sentenceIdToWords.put(id, words);
        }
    }
}
