package aggregator;

import java.util.ArrayList;
import java.util.HashSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RepairTriggerEntity {
        
    static HashSet<String> wrongEntityIds;
    static Document tmpDocument;
    
    // repairs the 'TRIGGER' attribute in nodes if it starts with 'E'
    // and also saves the wrong ENTITY ids so the nodes could be repaired later
    static void findWrongEIds(Node cNode) {

        if (cNode.getNodeName().equals("AFFECT") ||
            cNode.getNodeName().equals("KINSHIP") ||
            cNode.getNodeName().equals("SOCIAL")) {
            String triggerId = cNode.getAttributes().getNamedItem("TRIGGER").getNodeValue();
            if (triggerId.length() > 1 && triggerId.substring(0, 1).equals("E")) {
                // this is wrong
                wrongEntityIds.add(cNode.getAttributes().getNamedItem("TRIGGER").getNodeValue());
                // replace it here but you also need to replace in the node definition
                NamedNodeMap nnm = cNode.getAttributes();
                String newTriggerId = triggerId.replace('E', 'T');
                nnm.getNamedItem("TRIGGER").setNodeValue(newTriggerId);
            }
        }
        NodeList cList = cNode.getChildNodes();
        for (int i = 0; i < cList.getLength(); i++) {
            findWrongEIds(cList.item(i));
        }
    }
    
    // renames one ENTITY node to TRIGGER if it is the case
    static void repairIds(Node cNode) {
        
        if (cNode.getAttributes() != null &&
            cNode.getAttributes().getNamedItem("ID") != null &&
            wrongEntityIds.contains(cNode.getAttributes().getNamedItem("ID").getNodeValue())) {
            // RENAME <ENTITY ID = 'E23'> ... </ENTITY>
            // TO     <TRIGGER ID = 'T23'> ... </TRIGGER>
            
            System.out.printf("replace %s with %s\n", cNode.getAttributes().getNamedItem("ID").getNodeValue(),
                    cNode.getAttributes().getNamedItem("ID").getNodeValue().replace('E', 'T'));
            
            Node triggerNode = tmpDocument.createElement("TRIGGER");
            Attr tId = tmpDocument.createAttribute("ID");
            tId.setNodeValue(cNode.getAttributes().getNamedItem("ID").getNodeValue().replace('E', 'T'));
            
            triggerNode.getAttributes().setNamedItem(tId);
            NodeList sChilds = cNode.getChildNodes();
            ArrayList<Node> appendNodes = new ArrayList<>();
            for (int i = sChilds.getLength() - 1; i >=0; i--) {
                appendNodes.add(sChilds.item(i).cloneNode(true));
                cNode.removeChild(sChilds.item(i));
            }
            for (int i = 0; i < appendNodes.size(); i++) {
                triggerNode.appendChild(appendNodes.get(i));
            }
            cNode.appendChild(triggerNode);
        }
        NodeList cList = cNode.getChildNodes();
        for (int i = 0; i < cList.getLength(); i++) {
            repairIds(cList.item(i));
        }
    }
    
    static void repairSentences(NodeList sNodes, Document tmpDomDocument) {
        // these sentences are in one file (part of a single chapter)
        wrongEntityIds = new HashSet<>();
        tmpDocument = tmpDomDocument;
        for (int i = 0; i < sNodes.getLength(); i++) {
            findWrongEIds(sNodes.item(i));
        }
        for (int i = 0; i < sNodes.getLength(); i++) {
            repairIds(sNodes.item(i));
        }
    }
}
