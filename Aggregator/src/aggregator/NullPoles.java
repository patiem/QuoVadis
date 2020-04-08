
package aggregator;

import java.util.ArrayList;
import java.util.HashMap;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NullPoles {
    
    // from one node get the XML parent node that's an "S" node or null
    public static Node sentenceParent(Node cNode) {
        while (cNode != null && !cNode.getNodeName().equals("S")) {
            cNode = cNode.getParentNode();
        }
        return cNode;
    }
    
    public static void printNullPolesAKS(Document domDocument) {
        
        ArrayList<String> relNames = new ArrayList<>();
        
        relNames.add("AFFECT");
        relNames.add("KINSHIP");
        relNames.add("SOCIAL");
        
        HashMap<String, Node> triggerNode = new HashMap<>();
        
        NodeList triggerNodes = domDocument.getElementsByTagName("TRIGGER");
        for (int i = 0; i < triggerNodes.getLength(); i++) {
            Node cNode = triggerNodes.item(i);
            triggerNode.put(cNode.getAttributes().getNamedItem("ID").getNodeValue(),
                    cNode);
        }
        
        int cnt = 0;
        for (int i = 0; i < relNames.size(); i++) {
            String relName = relNames.get(i);
            
            NodeList nodesList = domDocument.getElementsByTagName(relName);
            for (int j = 0; j < nodesList.getLength(); j++) {
                Node cNode = nodesList.item(j);
            
                Node fromNode = ReferentialChains.entityNode.get(
                        cNode.getAttributes().getNamedItem("FROM").getNodeValue());

                Node toNode = ReferentialChains.entityNode.get(
                        cNode.getAttributes().getNamedItem("TO").getNodeValue());

                Node tNode = triggerNode.get(
                        cNode.getAttributes().getNamedItem("TRIGGER").getNodeValue());

                String nulls = "";

                if (cNode.getAttributes().getNamedItem("ID").getNodeValue().equals("KIN000400554")) {
                    int a = 1 + 1;
                }
                
                if (fromNode == null) {
                    nulls += "FROM ";
                }
                if (toNode == null) {
                    nulls += "TO ";
                }
                if (tNode == null) {
                    nulls += "TRIGGER ";
                }
                
                Node cNodeS = sentenceParent(cNode);
                
                if (cNodeS == null) {
                    nulls += "SENTENCE_PARENT";
                }
                
                if (nulls.length() > 0) {
                    cnt++;
                    System.out.printf("Relation ID : %s  NULL POLES : %s\n",
                            cNode.getAttributes().getNamedItem("ID"),
                            nulls);

                    if (cNodeS != null) {
                        System.out.printf("FILE : %s\n\n", Aggregator.sIdToFile.get(
                                cNodeS.getAttributes().getNamedItem("id").getNodeValue()));
                    }
                }
            }
        }
        System.out.printf("Got %d null poles relations.\n\n", cnt);
    }
}
