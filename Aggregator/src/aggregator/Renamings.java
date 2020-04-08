package aggregator;

import static aggregator.ReferentialChains.heads;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Renamings {
    
    public static void applyRenamingsAKSType(Document domDocument) {
        
        HashMap<String, HashMap<String, String>> renamingsMap = new HashMap<>();
        
        HashMap<String, String> affectRenamings = new HashMap<>();
        affectRenamings.put("fear", "fear-of");
        affectRenamings.put("fear-by", "fear-of");
        affectRenamings.put("friendship", "friend-of");
        affectRenamings.put("hates/sad", "hate");
        affectRenamings.put("loves/joy", "love");
        renamingsMap.put("AFFECT", affectRenamings);
        
        HashMap<String, String> kinshipRenamings = new HashMap<>();
        kinshipRenamings.put("sibling-of", "sibling");
        renamingsMap.put("KINSHIP", kinshipRenamings);
        
        System.out.printf("Renaming ...\n");
        
        int cnt = 0;
        for (String relation : renamingsMap.keySet()) {
            HashMap<String, String> renamings = renamingsMap.get(relation);
            NodeList nodeList = domDocument.getElementsByTagName(relation);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                for (String fromStr : renamings.keySet()) {
                    if (node.getAttributes().getNamedItem("TYPE").getNodeValue().equals(fromStr)) {
                        node.getAttributes().getNamedItem("TYPE").setNodeValue(
                                renamings.get(fromStr));
                        cnt++;
                    }
                }
            }
        }
        System.out.printf("Done renaming %d node attributes.\n\n", cnt);
    }
    
    // goes recursively trough sNode and it's children and saves up the words
    public static void getSentenceWordsRec(Node sNode, ArrayList<String> words) {
        NodeList children = sNode.getChildNodes();
        for (int i = 0 ; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeName().equals("W")) {
                words.add(node.getTextContent());
            } else {
                getSentenceWordsRec(node, words);
            }
        }
    }
    
    // For one sentence node returns the words it consists of in order
    public static String getSentenceWords(Node sNode) {
        ArrayList<String> words = new ArrayList<>();
        
        getSentenceWordsRec(sNode, words);
        
        String rez = "";
        for (int i = 0; i < words.size(); i++) {
            rez += words.get(i) + " ";
        }
        return rez;
    }
    
    public static void applyRenamingsEntityTypes(Document domDocument) throws FileNotFoundException, UnsupportedEncodingException {
        
        PrintWriter printWriter = new PrintWriter("entity_type_renamings.txt", "UTF-8");
        
        System.out.printf("Appling entity type renamings ...\n");
        HashMap<String, String> renamingsMap = new HashMap<>();
        
        renamingsMap.put("ADDRESS", "LOCATION");
        renamingsMap.put("PERSON_CLASS", "PERSON-CLASS");
        renamingsMap.put("PERSON_PART", "PERSON-PART");
        renamingsMap.put("PERSON_GROUP", "PERSON-GROUP");
        
        HashSet<String> printTypes = new HashSet<>();
        printTypes.add("ENTITY-INCLUDED");
        printTypes.add("OTHER");
        printTypes.add("PERSON/DIVINITY");
        
        NodeList eNodes = domDocument.getElementsByTagName("ENTITY");
        int cnt = 0, cntPrint = 0;
        for (int i = 0; i < eNodes.getLength(); i++) {                        
            Node eNode = eNodes.item(i);
            String prevType = eNode.getAttributes().getNamedItem("TYPE").getNodeValue();
            if (renamingsMap.containsKey(prevType)) {
                cnt++;
                eNode.getAttributes().getNamedItem("TYPE").setNodeValue(renamingsMap.get(prevType));
            }
            if (i % 5000 == 0) {
                System.out.printf("Renamed entity types for %6d / %6d entities\n", i, eNodes.getLength());
            }
            
            if (printTypes.contains(prevType)) {
                String entityId = eNode.getAttributes().getNamedItem("ID").getNodeValue();
                Node sNode = NullPoles.sentenceParent(eNode);
                String fromFile = Aggregator.sIdToFile.get(
                        sNode.getAttributes().getNamedItem("id").getNodeValue());
                
                printWriter.printf("Entity ID = %10s   TYPE = %16s   FILE = %s\nENTITY Text = %s\nCONTEXT = %s\n\n",
                        entityId, prevType,
                        fromFile,
                        getSentenceWords(eNode),
                        getSentenceWords(sNode));
                cntPrint++;
            }
        }
        printWriter.close();
        System.out.printf("Applied entity type renamings, changed %d entities and printed info about %d.\n\n",
                cnt, cntPrint);
    }
}
