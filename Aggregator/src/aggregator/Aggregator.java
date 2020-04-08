package aggregator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author paul
 */
public class Aggregator {
    
    private static boolean ORDER_BY_SENTENCE_IDS = true;

    static int SIDMUL = 100000; // const
    static DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    static DocumentBuilder documentBuilder;
    static ArrayList<Document> domDocuments;
    static HashMap<String, String> sIdToFile; // first sentence id -> file name
    static Document aggregatedDomDocument;
    
    static boolean isLetter(char c) {
        if (c >= 'a' && c <= 'z') return true;
        if (c >= 'A' && c <= 'Z') return true;
        return false;
    }
    
    static String leading0(String original, int finalLength) {
        while (original.length() < finalLength) {
            original = "0" + original;
        }
        return original;
    }
    static String newIdString(String oldValue, int sentenceId) {
        String prefix = "";
        int i;
        for (i = 0; isLetter(oldValue.charAt(i)); i++) {
            prefix += oldValue.charAt(i);
        }
        
        int oldNumeric = 0;
        try {
            oldNumeric = Integer.parseInt(oldValue.substring(i));
            if (oldNumeric >= SIDMUL * sentenceId) {
                return prefix + String.valueOf(oldNumeric);
            }
            return prefix + leading0(String.valueOf(SIDMUL * sentenceId + oldNumeric), 9);
        } catch (java.lang.NumberFormatException e) {
            System.out.printf("   There is one entity id = '%s' in file %s\n",  oldValue, sIdToFile.get(String.valueOf(sentenceId)));
            return prefix + leading0(String.valueOf(SIDMUL * sentenceId) + oldValue.substring(1), 9);
        }
    }
    
    static ArrayList<String> tagNames; // list of possible tag names
    static HashMap<String, String> idRenamingMap;
    // nodes with tag names in 'tagNames' ids -> sentence ids in the current file

    static void buildIdRenaming(Node cNode, int sentenceId) {
        if (cNode.getAttributes() != null &&
                cNode.getAttributes().getNamedItem("ID") != null) {
            String oldId = cNode.getAttributes().getNamedItem("ID").getNodeValue();
            idRenamingMap.put(oldId, newIdString(oldId, sentenceId));
        }
        
        NodeList nList = cNode.getChildNodes();
        for (int i = 0; i < nList.getLength(); i++) {
            Node ccNode = nList.item(i);
            buildIdRenaming(ccNode, sentenceId);
        }
    }

    static void combineIds(Node node, int sentenceId) {
        for (String tagName : tagNames) {
            if (node.getNodeName().equals(tagName)) {
                // this node is an annotation node so change the node id
                NamedNodeMap nnm = node.getAttributes();
                String idValue = nnm.getNamedItem("ID").getNodeValue();
                
                String newID = newIdString(idValue, sentenceId);
                nnm.getNamedItem("ID").setNodeValue(newID);
                
                if (!tagName.equals("ENTITY") && !tagName.equals("TRIGGER")) {
                    // then it should have 'FROM' and 'TO'
                    nnm = node.getAttributes();
                    String fromIdValue = nnm.getNamedItem("FROM").getNodeValue();
                    String fromNewID = idRenamingMap.get(fromIdValue);
                    nnm.getNamedItem("FROM").setNodeValue(fromNewID);
                    
                    String toIdValue = nnm.getNamedItem("TO").getNodeValue();
                    String toNewID = idRenamingMap.get(toIdValue);
                    nnm.getNamedItem("TO").setNodeValue(toNewID);
                    
                    if (!tagName.equals("REFERENTIAL")) {
                        String triggerIdValue = nnm.getNamedItem("TRIGGER").getNodeValue();
                        String triggerNewID = idRenamingMap.get(triggerIdValue);
                        nnm.getNamedItem("TRIGGER").setNodeValue(triggerNewID);
                    }
                }
            }
        }
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            combineIds(list.item(i), sentenceId);
        }
    }
    
    // main method that creates the new aggregated document
    static void buildAggregatedDomDocumet() {
        aggregatedDomDocument = documentBuilder.newDocument();
        
        Element root = (Element)aggregatedDomDocument.createElement("DOCUMENT");
        Node rooth = aggregatedDomDocument.appendChild(root);
        
        HashSet<Integer> sIds = new HashSet<>();
        
        for (int i = 0; i < domDocuments.size(); i++) {
            Document doc = domDocuments.get(i);
            NodeList sList = doc.getElementsByTagName("S");
            String fileName = sIdToFile.get(sList.item(0).getAttributes().getNamedItem("id").getNodeValue());
            System.out.printf("Aggregating file %s\n", fileName);
            
            NodeList sNodes = doc.getElementsByTagName("S"); // get all sentence nodes
            
            // map from any id to the sentence id in this file
            if (idRenamingMap == null) {
                idRenamingMap = new HashMap<>();
            } else {
                idRenamingMap.clear();
            }
            
            for (int j = 0; j < sNodes.getLength(); j++) {
                Node sNode = sNodes.item(j);
                String id = sNode.getAttributes().getNamedItem("id").getNodeValue(); // get the sentence id
                Integer idInt = Integer.parseInt(id); // as int
                buildIdRenaming(sNode, idInt);
            }
            
            // fix duplicated ids
            for (int j = 0; j < sNodes.getLength(); j++) {
                Node sNode = sNodes.item(j);
                String id = sNode.getAttributes().getNamedItem("id").getNodeValue(); // get the sentence id
                combineIds(sNode, Integer.parseInt(id));
            }
            
            // fix TRIGGER = 'E23' cases
            RepairTriggerEntity.repairSentences(sNodes, doc);
            for (int j = 0; j < sNodes.getLength(); j++) {
                Node sNode = sNodes.item(j);
                String id = sNode.getAttributes().getNamedItem("id").getNodeValue(); // get the sentence id
                Integer idInt = Integer.parseInt(id); // as int
                if (!sIds.contains(idInt)) {
                    // this is a new sentence
                    Node insertNode = sNode.cloneNode(true);
                    rooth.appendChild(aggregatedDomDocument.adoptNode(insertNode));
                    sIds.add(idInt);
                } else {
                    System.out.printf("Sentence %s is duplicated\n", id);
                }
            }
        }
        
        System.out.println("Aggregated dom document created!");
    }
    
    static void writeAggregatedDocument() throws TransformerConfigurationException, TransformerException {
        
        // write the content into xml file
	TransformerFactory transformerFactory = TransformerFactory.newInstance();
	Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
	DOMSource source = new DOMSource(aggregatedDomDocument);
	StreamResult result = new StreamResult(new File("Quo Vadis.xml"));
 
	// Output to console for testing
	// StreamResult result = new StreamResult(System.out);
 
	transformer.transform(source, result);
 
	System.out.println("File Quo Vadis.xml saved!");
    }
     
    // order the domDocuments array by first sentence ids asscending
    static void orderBySentenceIds() {
        Collections.sort(domDocuments, new OrderBySentenceComparator());
    }
    static void orderByComplete() {
        Collections.sort(domDocuments, new OrderByCompleteComparator());
    }
    
    // for a document and a node name, return the number of nodes with that name in the document
    public static int countNodesByName(Document doc, String nodeName) {
        return doc.getElementsByTagName(nodeName).getLength();
    }
    
    // print the file names, sentence ids and number of annotations
    static void printfFilesStats() {
        int prevLastId = 0;
        
        // 'flag'
        boolean orderBySentenceIds = ORDER_BY_SENTENCE_IDS;
        if (!orderBySentenceIds) {
            orderByComplete();
        }
        for (int i = 0; i < domDocuments.size(); i++) {
            Document doc = domDocuments.get(i);
            
            NodeList sList = doc.getElementsByTagName("S");
            String firstId = sList.item(0).getAttributes().getNamedItem("id").getNodeValue();
            int firstSentenceId = Integer.parseInt(firstId);
            int lastSentenceId = Integer.parseInt(
                    sList.item(sList.getLength()-1).getAttributes().getNamedItem("id").getNodeValue());
            
            int sentences = countNodesByName(doc, "S");
            int E = countNodesByName(doc, "ENTITY");
            int R = countNodesByName(doc, "REFERENTIAL");
            int T = countNodesByName(doc, "TRIGGER");
            int A = countNodesByName(doc, "AFFECT");
            int K = countNodesByName(doc, "KINSHIP");
            int S = countNodesByName(doc, "SOCIAL");
            
            //System.out.printf("%45s Sentence ids = [%4d %4d]  E = %4d  R = %4d  T = %4d  A + K + S = %4d  ALL = %4d  complete = %f",
            System.out.printf("%45s Sentence ids =[%4d %4d] E = %4d  R = %4d T = %3d AKS = %3d ALL = %4d comp = %f",
                    sIdToFile.get(firstId), firstSentenceId, lastSentenceId,
                    E, R, T, A + K + S, E + R + A + K + S,
                    (double)(E + 2 * R + 5 * (A + K + S)) / sentences);
            
            if (orderBySentenceIds && firstSentenceId != prevLastId + 1) {
                System.out.printf(" Sentence ids are not in order!!\n");
                // this should not happen
            } else {
                System.out.printf("\n");
            }
            
            prevLastId = lastSentenceId;
        }
    }
    
    /* 
     * Reads REFERENTIAL IDS form a manually built file and deletes those nodes
     * from the aggregated document
     */
    static void deleteIncorrectReferentialRel() throws IOException {
        
        HashSet<String> delRefs = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader("delete_ref_ids.txt"))) {
            String line;
            System.out.printf("\nREFERENTIAL IDs to delete : ");
            while ((line = br.readLine()) != null) {
               System.out.printf("%s ", line);
               delRefs.add(line);
            }
            System.out.println();
        }
        
        NodeList refs = aggregatedDomDocument.getElementsByTagName("REFERENTIAL");
        System.out.printf("\nDeleting incorrect referential Ids ...\n");
        for (int i = 0; i < refs.getLength(); i++) {
            Node refNode = refs.item(i);
            if (i % 5000 == 0) {
                System.out.printf("PROGRESS %5d / %5d\n", i, refs.getLength());
            }
            if (delRefs.contains(refNode.getAttributes().getNamedItem("ID").getNodeValue())) {
                NodeList children = refNode.getChildNodes();
                Node parent = refNode.getParentNode();
                for (int j = 0; j < children.getLength(); j++) if (children.item(j) != null) {
                    parent.insertBefore(children.item(j).cloneNode(true), refNode);
                }
                parent.removeChild(refNode);
            }
        }
    }
    
    static void writeCountStats() {
        CountStats.writeCountStats(aggregatedDomDocument);
    }
    
    /**
     * Aggregates all files in ./annotations
     * Produces the aggregated file
     */
    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, TransformerConfigurationException, TransformerException {
        
        documentBuilder = documentBuilderFactory.newDocumentBuilder();
        domDocuments = new ArrayList<>();
        File localFolder = new File("annotations");
        sIdToFile = new HashMap<>();
        
        for (File xmlFile : localFolder.listFiles()) {
            if (xmlFile.getName().endsWith(".xml")) {
                xmlFile = new File("annotations\\" + xmlFile.getName());
                Document domDocument = documentBuilder.parse(xmlFile);
                domDocuments.add(domDocument);
                NodeList sList = domDocument.getElementsByTagName("S");
                for (int i = 0; i < sList.getLength(); i++) {
                    Node sNode = sList.item(i);
                    sIdToFile.put(
                        sNode.getAttributes().getNamedItem("id").getNodeValue(),
                        xmlFile.getName());
                }
            }
        }
        
        orderBySentenceIds();
        printfFilesStats();
        System.out.printf("\n\n\n");
        
        tagNames = new ArrayList<>();
        tagNames.add("ENTITY");
        tagNames.add("REFERENTIAL");
        tagNames.add("TRIGGER");
        tagNames.add("AFFECT");
        tagNames.add("KINSHIP");
        tagNames.add("SOCIAL");
        
        buildAggregatedDomDocumet();
        deleteIncorrectReferentialRel();
        
        ReferentialChains.domDocument = aggregatedDomDocument; // wired
        ReferentialChains.buildEntityNodeMapping(); // wired
        ReferentialChains.uniteReferentialChains();
        ReferentialChains.buildReferentialChains(aggregatedDomDocument);
        ReferentialChains.printDifferentNumberGenderChains();
        ReferentialChains.printOnlyPronounsChains();
        
        NodeList wList = aggregatedDomDocument.getElementsByTagName("W");
        NodeList sList = aggregatedDomDocument.getElementsByTagName("S");
        
        Renamings.applyRenamingsAKSType(aggregatedDomDocument);
        Renamings.applyRenamingsEntityTypes(aggregatedDomDocument);
        
        // NullPoles.printNullPolesAKS(aggregatedDomDocument);
        
        int nrEntities = aggregatedDomDocument.getElementsByTagName("ENTITY").getLength();
        System.out.printf("There are %d entities.", nrEntities);
        
        NamesResolution.readNamesFromRefUnification();
        NamesResolution.addChainInformation();
        NamesResolution.buildEntityToWords();
        NamesResolution.buildEntityToChainWords();
        NamesResolution.readNameClasses();
        NamesResolution.buildBasedOnNameClassesAndChains();
        NamesResolution.printEntitiyNameMapping();
        
        System.out.printf("There are %d W nodes.\n", wList.getLength());
        
        HashSet<String> annotationTags = new HashSet<>();
        //annotationTags.add("ENTITY");
        //annotationTags.add("TRIGGER");
        annotationTags.add("REFERENTIAL");
        annotationTags.add("KINSHIP");
        annotationTags.add("SOCIAL");
        annotationTags.add("AFFECT");
        int cnt = 0, cnt2 = 0;
        for (int i = 0; i < wList.getLength(); i++) {
            Node wNode = wList.item(i);
            boolean one = false;
            while (wNode.getParentNode() != null) {
                if (annotationTags.contains(wNode.getNodeName()) &&
                        (!wNode.getNodeName().equals("REFERENTIAL")) ||
                            (wNode.getNodeName().equals("REFERENTIAL") &&
                             !wNode.getAttributes().getNamedItem("TYPE").getNodeValue().contains("coref"))) {
                    cnt++;
                    one = true;
                }
                wNode = wNode.getParentNode();
            }
            if (one) {
                cnt2++;
            }
        }
        System.out.printf("There are %d W nodes under annotations distinct.\n", cnt2);
        System.out.printf("There are %d W nodes under annotations not distinct.\n", cnt);
        
        ReferentialChains.printReferentialChainsByLength();
        
        //writeCountStats();
        writeAggregatedDocument();       
    }
}
