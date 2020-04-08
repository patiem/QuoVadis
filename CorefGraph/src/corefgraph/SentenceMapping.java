package corefgraph;

import static corefgraph.Graph.domDocument;
import java.util.HashMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SentenceMapping {
    
    public static HashMap<String, String> entityToSId;
    
    public static void buildEntityToSentenceIdMapping() {
        entityToSId = new HashMap<>();
        
        NodeList eList = domDocument.getElementsByTagName("ENTITY");
        for (int i = 0; i < eList.getLength(); i++) {
            Node eNode = eList.item(i);
            String id = eNode.getAttributes().getNamedItem("ID").getNodeValue();
            
            while (eNode != null && !eNode.getNodeName().equals("S")) {
                eNode = eNode.getParentNode();
            }
            
            if (eNode == null) {
                CorefGraph.printTime(); System.out.printf("No <S> parent node for entity = %s .\n", id);                
            } else {
                entityToSId.put(id, eNode.getAttributes().getNamedItem("id").getNodeValue());
            }
        }
    }    
}
