
package aggregator;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ReferentialChains {

    public static Document domDocument;
    
    // "E25" -> "ENTITY" Node with this id
    static HashMap<String, Node> entityNode;
    public static void buildEntityNodeMapping() {
         entityNode = new HashMap<>();
         NodeList nodeList = domDocument.getElementsByTagName("ENTITY");
         System.out.println();
         for (int i = 0; i < nodeList.getLength(); i++) {
             Node cNode = nodeList.item(i);
             entityNode.put(cNode.getAttributes().getNamedItem("ID").getNodeValue(), cNode);
             if (i % 5000 == 0) {
                System.out.printf("Parsed %6d / %6d entities.\n", i, nodeList.getLength());
             }
         }
         System.out.println("Built Entity -> Node mapping!");
    }
    
    // if REFERENTIAL FROM="E6" TO="E3" TYPE="coref"
    // then "E3" -> "E6"
    static HashMap<String, String> parentRef;
    static HashMap<String, String> refRelRef; // add the REFERENTIAL relation id as well
    static void addEdge(String fromId, String toId, String refRelId) {
        if (parentRef.containsKey("fromId")) {
            System.out.printf("DOUBLE PARENTS %s !!! \n", fromId);
        }
        parentRef.put(fromId, toId);
        refRelRef.put(fromId, refRelId);
    }
    
    // highest order ancestor
    static HashMap<String, String> parentRefRec;
    static TreeMap<String, ArrayList<String>> heads; // all Enitites that are 'heads' (values in parentRefRec)
    // sorted is better

    static String tmp;
    static void appendChildrenRec(Node cNode) {
        if (cNode.getNodeName().equals("W")) {
            tmp += " " + cNode.getTextContent();
        }
        NodeList cNodes = cNode.getChildNodes();
        for (int i = 0; i < cNodes.getLength(); i++) {
            Node node = cNodes.item(i);
            appendChildrenRec(node);
        }
    }

    static String prettyString(String entityId) {
        String ret =  entityId ;
        ret += " [";
        try {
        if (entityNode.get(entityId) != null) {
            tmp = "";
            appendChildrenRec(entityNode.get(entityId));
            if (tmp.length() >= 1) {
                tmp = tmp.substring(1);
                ret += tmp.replaceAll("(\\r|\\n)", "").replaceAll("( )+", " ");
            }
        }
        } catch (Exception e ) {
            System.out.printf("EXCEPTION for %s\n", entityId);
            throw e;
        }
        ret += "]";
        return ret;
    }
    
    static TreeSet<String> properNounSet;
    static int isName(String name) {
        String name2 = prettyString(name);
        name2 = name2.substring(name2.indexOf('[') + 1);
        if (name2.length() > 0 && name2.charAt(0) >= 'A' && name2.charAt(0) <= 'Z') { 
            // capital letter but we will also check for proper noun information
            Node cNode = entityNode.get(name);
            if (cNode == null) return 0;
            
            while (cNode != null && cNode.hasChildNodes() && cNode.getNodeName() != null && !cNode.getNodeName().equals("W")) {
                cNode = cNode.getChildNodes().item(0);
            }
            if (cNode == null || cNode.getAttributes() == null) {
                return 0;
            }
            if (cNode.getAttributes().getNamedItem("POS") != null &&
                cNode.getAttributes().getNamedItem("POS").getNodeValue().equals("NOUN") && 
                cNode.getAttributes().getNamedItem("Type") != null &&
                cNode.getAttributes().getNamedItem("Type").getNodeValue().equals("proper")) {
                //properNounSet.add(cNode.getTextContent());
                return 1;
            }
            if (properNounSet.contains(cNode.getTextContent())) {
                // the list of predefined proper nouns
                return 1;
            }
            return 0;
        }
        return 0;
    }
    
    static boolean filterChain(String head, ArrayList<String> children) {
        if (children.isEmpty()) {
            return false;
        }
        int nrNames = isName(head);
        for (int i = 0; i < children.size(); i++) {
            nrNames += isName(children.get(i));
        }
        return (nrNames >= 1);
    }
    
    static HashSet<String> blacklistHeads = new HashSet<>();
    static void readBlackListRefChains() throws FileNotFoundException, IOException {
        try (BufferedReader br = new BufferedReader(new FileReader("blacklist_ref_heads.txt"))) {            
            String line;
            while ((line = br.readLine()) != null) {
               String[] parts = line.split("\\s+");
               if (parts.length > 0) {
                   blacklistHeads.add(parts[0]);
               }
            }
        }
    }
    
    static void printReferentialChains() throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter printWriter = new PrintWriter("ref_chains.txt", "UTF-8")) {
            
            Set<Entry<String, ArrayList<String>>> entrySet = heads.entrySet();
            int nrMatch = 0;
            for (Entry entry : entrySet) {
                String head = (String) entry.getKey();
                ArrayList<String> children = (ArrayList<String>) entry.getValue();
                Collections.sort(children);

                if (!blacklistHeads.contains(head)) { //filterChain(head, children)) {
                //if (true) {
                    
                    nrMatch++;
                    printWriter.printf("HEAD     : %s\nCHILDREN : ", prettyString(head));
                    
                    for (int i = 0; i < children.size(); i++) {
                        printWriter.printf("%s ", refRelRef.get(children.get(i)));
                        printWriter.printf("%s  ", prettyString(children.get(i)));
                    }
                    printWriter.println();
                    if (head.length() < 1) {
                        int a = 1 + 1;
                    }
                    
                    int sentenceId = Integer.parseInt(head.substring(1)) / Aggregator.SIDMUL;
                    printWriter.printf("SID&ID   : %4d  E%d\n", sentenceId,
                            Integer.parseInt(head.substring(1)) % Aggregator.SIDMUL);
                    
                    printWriter.printf("FILE:    : %s\n\n", Aggregator.sIdToFile.get(new Integer(sentenceId).toString()));
                }
            }
            
            printWriter.printf("\nThere are %d chains that match filter out of %d\n\n", nrMatch, entrySet.size());
            printWriter.close();
        }
    }
    
    // prints the top 10 referential chains
    public static void printReferentialChainsByLength() throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter printWriter = new PrintWriter("ref_chains_top.txt", "UTF-8")) {
            
            ArrayList<String> headsCount = new ArrayList<>();
            Set<Entry<String, ArrayList<String>>> entrySet = heads.entrySet();
            for (Entry entry : entrySet) {
                String head = (String) entry.getKey();
                ArrayList<String> children = (ArrayList<String>) entry.getValue();
                headsCount.add(head);
            }
            Collections.sort(headsCount, new OrderByChainLengthComparator());
            
            HashMap<String, Integer> aksCnt = new HashMap<>();
            HashMap<String, Integer> refCnt = new HashMap<>();
            HashMap<String, Integer> headAksCnt = new HashMap<>();
            HashMap<String, Integer> headRefCnt = new HashMap<>();
            for (int i = 0; i < 20; i++) { //headsCount.size(); i++) {
                String head = headsCount.get(i);
                aksCnt.put(head, 0);
                refCnt.put(head, 0);
                ArrayList<String> children = heads.get(head);
                for (int j = 0; j < children.size(); j++) {
                    aksCnt.put(children.get(j), 0);
                    refCnt.put(children.get(j), 0);
                    
                    headAksCnt.put(parentRefRec.get(children.get(i)), 0);
                    headRefCnt.put(parentRefRec.get(children.get(i)), 0);
                }
            }
            ArrayList<String> akss = new ArrayList<>();
            akss.add("AFFECT");
            akss.add("SOCIAL");
            akss.add("KINSHIP");
            
            for (int i = 0; i < akss.size(); i++) {
                String rel = akss.get(i);
                NodeList nodes = Aggregator.aggregatedDomDocument.getElementsByTagName(rel);
                for (int j = 0; j < nodes.getLength(); j++) {
                    Node node = nodes.item(j);
                    
                    String from = node.getAttributes().getNamedItem("FROM").getNodeValue();
                    String to = node.getAttributes().getNamedItem("TO").getNodeValue();
                    
                    if (aksCnt.get(from) != null) {
                        aksCnt.put(from, aksCnt.get(from) + 1);
                    }
                    if (aksCnt.get(to) != null) {
                        aksCnt.put(to, aksCnt.get(to) + 1);
                    }
                    if (refCnt.get(from) != null) {
                        refCnt.put(from, refCnt.get(from) + 1);
                    }
                    if (refCnt.get(to) != null) {
                        refCnt.put(to, refCnt.get(to) + 1);
                    }
                    
                    if (headAksCnt.get(parentRefRec.get(from)) != null) {
                        headAksCnt.put(parentRefRec.get(from), headAksCnt.get(parentRefRec.get(from)) + 1);
                    }
                    if (headAksCnt.get(parentRefRec.get(to)) != null) {
                        headAksCnt.put(parentRefRec.get(to), headAksCnt.get(parentRefRec.get(to)) + 1);
                    }
                    
                }
            }
            NodeList nodes = Aggregator.aggregatedDomDocument.getElementsByTagName("REFERENTIAL");
            for (int j = 0; j < nodes.getLength(); j++) {
                Node node = nodes.item(j);
                if (node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("coref")) {
                    continue;
                }
                if (node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("coref-interpret")) {
                    continue;
                }
                    
                String from = node.getAttributes().getNamedItem("FROM").getNodeValue();
                String to = node.getAttributes().getNamedItem("TO").getNodeValue();
                    
                if (refCnt.get(from) != null) {
                    refCnt.put(from, aksCnt.get(from) + 1);
                }
                if (refCnt.get(to) != null) {
                    refCnt.put(to, aksCnt.get(to) + 1);
                }
                if (headRefCnt.get(parentRefRec.get(from)) != null) {
                    headRefCnt.put(parentRefRec.get(from), headRefCnt.get(parentRefRec.get(from)) + 1);
                }
                if (headRefCnt.get(parentRefRec.get(to)) != null) {
                    headRefCnt.put(parentRefRec.get(to), headRefCnt.get(parentRefRec.get(to)) + 1);
                }
            }
            
            for (String entity : aksCnt.keySet()) {
                printWriter.printf("%s  AKS cnt = %d REF cnt = %d\n", entity, aksCnt.get(entity), refCnt.get(entity));
            }
            
            for (int i = 0; i < 20; i++) {
                printWriter.printf("%s\n", headsCount.get(i));

                
                String head = headsCount.get(i);
                ArrayList<String> children = heads.get(head);

                printWriter.printf("HEAD     : %s\nCHILDREN : ", prettyString(head));
                printWriter.printf("SIZE     : %d\n", children.size() + 1);
                printWriter.printf("AKS rels : %d\n", headAksCnt.get(head));
                printWriter.printf("REF rels : %d\n", headRefCnt.get(head));
                
                for (int j = 0; j < children.size(); j++) {
                    printWriter.printf("%s ", refRelRef.get(children.get(j)));
                    printWriter.printf("%s  ", prettyString(children.get(j)));
                }
                printWriter.println();
                int sentenceId = Integer.parseInt(head.substring(1)) / Aggregator.SIDMUL;
                printWriter.printf("SID&ID   : %4d  E%d\n", sentenceId,
                        Integer.parseInt(head.substring(1)) % Aggregator.SIDMUL);
                    
                printWriter.printf("FILE:    : %s\n\n", Aggregator.sIdToFile.get(new Integer(sentenceId).toString()));
            }
            printWriter.close();
        }
    }

    // for entity - character name matching
    static void buildWordsSetRec(Node node, HashSet<String> words) {
        if (node == null) {
            return;
        }
        if (node.getNodeName().equals("W")) {
            // POS="NOUN" Type="proper"
            if (node.getAttributes().getNamedItem("POS") != null && 
                node.getAttributes().getNamedItem("POS").getNodeValue().equals("NOUN") &&
                node.getAttributes().getNamedItem("Type") != null && 
                node.getAttributes().getNamedItem("Type").getNodeValue().equals("proper")) {
                words.add(node.getTextContent());
            }
        } else {
            NodeList cList = node.getChildNodes();
            for (int i = 0; i < cList.getLength(); i++) {
                buildWordsSetRec(cList.item(i), words);
            }
        }
    }
    
    // for entity - character name matching
    static void buildWordsSetRecSecond(Node node, HashSet<String> words) {
        if (node == null) {
            return;
        }
        if (node.getNodeName().equals("W")) {
            // POS="NOUN" Type="proper"
            if (node.getAttributes().getNamedItem("POS") != null && 
                node.getAttributes().getNamedItem("POS").getNodeValue().equals("NOUN")) {
                words.add(node.getTextContent());
            }
        } else {
            NodeList cList = node.getChildNodes();
            for (int i = 0; i < cList.getLength(); i++) {
                buildWordsSetRecSecond(cList.item(i), words);
            }
        }
    }
    
    public static int entitiesMappedCnt = 0;
    // for entity - character name matching
    static void printReferentialChainsSecond() throws FileNotFoundException, UnsupportedEncodingException {
        

        try (PrintWriter printWriter = new PrintWriter("ref_chains.txt", "UTF-8")) {
            
            Set<Entry<String, ArrayList<String>>> entrySet = heads.entrySet();
            for (Entry entry : entrySet) {
                String head = (String) entry.getKey();
                
                if (blacklistHeads.contains(head)) {
                    continue;
                }
                
                ArrayList<String> children = (ArrayList<String>) entry.getValue();
                
                HashSet<String> properNouns = new HashSet<>();
                buildWordsSetRec(entityNode.get(head), properNouns);
                for (int i = 0; i < children.size(); i++) {
                    buildWordsSetRec(entityNode.get(children.get(i)), properNouns);
                }
                
                printWriter.printf("%s ", head);
                for (int i = 0; i < children.size(); i++) {
                    printWriter.printf("%s ", children.get(i));
                }
                entitiesMappedCnt += children.size() + 1;
                printWriter.printf("\n");
                
                if (properNouns.size() == 0) {
                    buildWordsSetRecSecond(entityNode.get(head), properNouns);
                    for (int i = 0; i < children.size(); i++) {
                        buildWordsSetRecSecond(entityNode.get(children.get(i)), properNouns);
                    }
                }
                for (String noun : properNouns) {
                    printWriter.printf("%s ", noun);                                       
                }
                printWriter.println();
                printWriter.println();
            }
            printWriter.close();
        }
    }

    static void buildReferentialChains(Document aggDomDocument) throws FileNotFoundException, UnsupportedEncodingException, IOException {
        
        domDocument = aggDomDocument;
        buildEntityNodeMapping();
        
        parentRef = new HashMap<>();
        refRelRef = new HashMap<>();
        NodeList refNodes = domDocument.getElementsByTagName("REFERENTIAL");
        for (int i = 0; i < refNodes.getLength(); i++) {
            Node node = refNodes.item(i);
            if (node.getAttributes().getNamedItem("TYPE") == null) {
                int a = 2 + 2;
            }
            if (node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("coref") ||
                node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("coref-interpret") ||
                node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("name-of") ||
                node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("has-name") ||
                node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("isa") ||
                node.getAttributes().getNamedItem("TYPE").getNodeValue().equals("class-of")) {
                // this is a coref node
                addEdge(node.getAttributes().getNamedItem("FROM").getNodeValue(),
                        node.getAttributes().getNamedItem("TO").getNodeValue(),
                        node.getAttributes().getNamedItem("ID").getNodeValue());
            }
        }
        
        parentRefRec = new HashMap();
        Set<Entry<String, String>> entrySet = parentRef.entrySet();
        int cnt = 0;
        heads = new TreeMap<>();
        for (Entry entry : entrySet) {
            
            String entity = (String) entry.getKey();
            String parent = (String) entry.getValue();
            
            // entity -> parent
            HashSet<String> used = new HashSet<>();
            while (parentRef.containsKey(parent) &&
                   !used.contains(parent)) {
                
                used.add(parent);
                parent = parentRef.get(parent);
            }
            parentRefRec.put(entity, parent);
            
            if (heads.get(parent) == null) {
                ArrayList<String> tmpp = new ArrayList<>();
                tmpp.add(entity);
                heads.put(parent, tmpp);
            } else {
                heads.get(parent).add(entity);
            }
            cnt++;
            if (cnt % 5000 == 0) {
                System.out.printf("Built %d / %d referential chains.\n", cnt, entrySet.size());
            }
        }
        
        buildProperNounsSet();
        //printReferentialChains();
        
        readBlackListRefChains();
        // this would be for entities - character mapping
        printReferentialChains();
        System.out.printf("Mapped %d entities.\n", entitiesMappedCnt);
    }
    
    static void buildProperNounsSet() {
        
        String allNounsSpace =
                  "AChilonides Acilius Acleii Acteea Acteii Acteon Aemilius Afer Afranius Africanus "
                + "Agamemnon Agripinei Agrippa Agrippina Ahenobarbus Ahile Alcmeon  Aliturus Anacreon "
                + "Anneus Antemios Antichrist Antichriste Antichristului Antistia Antistius Aper Apicius "
                + "Apollmis Apollonius Aquilinus Araricus Ares  Arulanus Atacinus Ate Atelius Atnensis "
                + "Attalus Augustus Aulus Avirnus Bachus Barbă-Arămie Bassus Benevent Berenice Brennus Britanicus "
                + "Brutus Burrus Cacus  Caelius Caesar Caia Caile Caius Calendio Calicratus Caligula Callina Callinei "
                + "Callino Calpurnius Calvia Capena Capitoliu Capitoliul Capitoliului Capua Carinae Carinas Carine Caron Cefas Cel-de-Sus "
                + "Celer Ceres Cezar Cezare Chilon Chilone Chilonides Chirstos Chrestos Chrisos Christe Christo Christoase "
                + "Christos Christus Chrsitoase Chrysothemis Cibela Claucus Claudius Clemens Clio Clivus Collis Corbulon "
                + "Cornelius Cornutus Craecina Cresida Crispinilla Crispinillei Crispinus Cryzothemis Danaei Delphini "
                + "Demas Demetrei Deucalion Diana Dianei Diodor Diogene Domitius Drussus Drusus Dumnezeu Emilius  Enea "
                + "Eol Epafrodit Eschil Esquilin Esquilina Esquilinului Esquilm Euncius Eunice Eunicei Euricius Euterprei "
                + "Evander Evei Fabricius Faon Fenius Festus Flavii Flavius Flegon Gaius Gallon Gelocius Germanicus Glaucus "
                + "Graecina Gratus Hasta Hecate Helenae Helios Helius Hemes Hera Heracle Heraclea Heracles Heraclit Hermes "
                + "Heu Hibernia Hic Hister Histrio Homer Horatius Horaţiu Ida Idomen Iehova Ioan Isis Isus Ityl Iulia "
                + "Iuliei Iulius Iullianum Iunia Iuno Iunona Iunonei Jehova Julia Julius Jupiter Keops Koma Lanio Lateranus "
                + "Latonei Laurentum Laureolus Lecanius Ledei Libitina Libitinei Licinius Ligia  Ligiei Ligio Lilith Linus Lucan "
                + "Lucius Lucreţia Lucreţiu Lucullus Magdalena Marcellus Marcinus Marcus Mariei Marte Martialis Martinianus "
                + "Massinissa  Memnon Menicles Merion Midas Mihtra-Baal Minutius Miriam Mithra Mitridate  Musonius Naumachia "
                + "Nausica Nazanus Nazarius Nemesis Nepos Nereus Nero Neroina Neroni Nerulinus Nerva Nessus Niger Nigidia "
                + "Nimfidius Niobizilor Novius Numa  Octavia Oreste Orfeu Osiris Ostonus Otho Ovidiu Padanius Pallas Paris "
                + "Pasitea Patricius Pavel Pedanius Penelopă Persefona Persius Petre Petronius Petru Petrus Pilat Pison "
                + "Pistorium Pitagora Plautilla Plautillei Plautius Plautus Politetes Pollion Pollux Pompeius Pompilius "
                + "Pompoma Pompomei Pomponia Pomponiei Pomponius Pontius Popeea Popeii Popmonia Poppea Poppeii Priam Priscus "
                + "Processus Proculus Prometeu Prometeul Prosperina Proximus Psyche Pudens Pyrrhon Quartus Quintianus Regulus "
                + "Rubelius Rubria Rufinus Rufius Rufus Sabinus Sabrius Saturn Saturninus Scevinus Scribonia Secundus Selene "
                + "Seneca Senecion Serapis Servius Severus Sextus Sido Sienkiewicz Sifax Silana Silanus Silvia Sirius Smintheus "
                + "Socrate Sofronius Solina Soracte Soractelui Spartacus Statius Steros Subrius Sulpicius Syfax Tallianum "
                + "Telesinus Teocles Teresias Termus Terpiros Terporis Terpros Tersites Tezeus Thabita Thanatos Theocles Thraseas "
                + "Tiberiadei Tiberiu Tiberius Tigeflinus Tigellinus Tiggellinus Tiresias Tiridates Titus Trimalchion Troilus "
                + "Tugurmus Tullius Tuscus Ulise Urban Ursus Vagio Valena Vanius Vannius Varinius Vatinius Vatinus Velento Venerei "
                + "Venus Vergiliu Vespasian Vesta Vestinus Vetus Vibilius Vicinius Vicinus Vicus Vincius Vinicius Viniciusilor "
                + "Viniciuşii Vinicus Vitelius Vitellius Vlnicius Zethos Zeus Ștefan Șofron";
        
        String nouns[] = allNounsSpace.split(" ");
        properNounSet = new TreeSet<>();
        properNounSet.addAll(Arrays.asList(nouns));
        System.out.printf("There are %d proper nouns (hardcoded).\n", properNounSet.size());
    }
    
    static void getWordsRecursive(Node cNode, ArrayList<Node> wordNodes) {
        NodeList nodeList = cNode.getChildNodes();
        
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeName().equals("W")) {
                wordNodes.add(node);
            } else {
                getWordsRecursive(node, wordNodes);
            }
        }
    }

    static String getAttributeValue(Node node, String attName) {
        if (node.getAttributes().getNamedItem(attName) != null) {
            return node.getAttributes().getNamedItem(attName).getNodeValue();
        }
        return "";
    }
    
    static void addAttributes(String entityId, HashMap<String, ArrayList<String>> attToEnitties,
            String attributeName) {
       
        Node eNode = entityNode.get(entityId);
        if (eNode == null) {
            return;
        }
        
        ArrayList<Node> wordNodes = new ArrayList<>();
        getWordsRecursive(eNode, wordNodes);
        for (int i = 0; i < wordNodes.size(); i++) {
            Node wNode = wordNodes.get(i);
            
            if ((getAttributeValue(wNode, "POS").equals("NOUN") && !getAttributeValue(wNode, "Type").equals("proper")) ||
                (getAttributeValue(wNode, "POS").equals("PRONOUN") && !getAttributeValue(wNode, "Type").equals("possessive") &&
                (getAttributeValue(wNode, "Case").equals("oblique") && (!wNode.getTextContent().equals("i") && !wNode.getTextContent().equals("îi"))))) {
                
                if (wNode.getAttributes().getNamedItem(attributeName) != null) {
                    String attValue = wNode.getAttributes().getNamedItem(attributeName).getNodeValue();

                    ArrayList<String> entities = attToEnitties.get(attValue);
                    if (entities == null) {
                        entities = new ArrayList<>();
                        attToEnitties.put(attValue, entities);
                    }
                    entities.add(prettyString(entityId));
                }
            }
        }
    }
    
    static void printDifferentNumberGenderChains() throws FileNotFoundException, UnsupportedEncodingException {
        
        Set<Entry<String, ArrayList<String>>> entrySet = heads.entrySet();
        try (PrintWriter printWriter = new PrintWriter("ref_chains_filter_NG.txt", "UTF-8")) {
            
            int suspectChains = 0;
            for (Entry entry : entrySet) {
                String head = (String) entry.getKey();
                ArrayList<String> children = (ArrayList<String>) entry.getValue();
                
                ArrayList<String> attNames = new ArrayList<>();
                attNames.add("Number");
                attNames.add("Gender");
                HashMap<String, ArrayList<String>> attToEntities[] = new HashMap[2];
                
                for (int i = 0; i < 2; i++) {
                    String attName = attNames.get(i);
                    attToEntities[i] = new HashMap<>();
                    
                    addAttributes(head, attToEntities[i], attName);
                
                    for (String ch : children) {
                        addAttributes(ch, attToEntities[i], attName);
                    }
                }
                
                if (attToEntities[0].size() > 1 || attToEntities[1].size() > 1) {
                    
                    suspectChains++;
                        
                    printWriter.printf("\nHEAD     : %s\nCHILDREN : ", prettyString(head));
                    for (int i = 0; i < children.size(); i++) {
                       printWriter.printf("%s ", refRelRef.get(children.get(i)));
                       printWriter.printf("%s  ", prettyString(children.get(i)));
                    }
                    printWriter.println();
                    int sentenceId = Integer.parseInt(head.substring(1)) / Aggregator.SIDMUL;
                    printWriter.printf("SID&ID   : %4d  E%d\n", sentenceId,
                            Integer.parseInt(head.substring(1)) % Aggregator.SIDMUL);

                    printWriter.printf("FILE:    : %s\n", Aggregator.sIdToFile.get(new Integer(sentenceId).toString()));
                    
                    for (int i = 0; i < 2; i++) {
                        if (attToEntities[i].size() > 1) {
                            
                            for (String attVal : attToEntities[i].keySet()) {
                                printWriter.printf("ENTITIES in CC with %7s = %7s : ", attNames.get(i), attVal);
                                
                                ArrayList<String> entitiesArray = attToEntities[i].get(attVal);
                                
                                for (int k = 0; k < entitiesArray.size(); k++) {
                                    printWriter.printf("%s ", entitiesArray.get(k));
                                }
                                printWriter.printf("\n");
                            }
                        }
                    }
                }
            }
            printWriter.printf("%5d / %5d suspect chains.\n\n", suspectChains, entrySet.size());
        }
    }
    
    // true if all the <W> nodes under this entity are pronous
    static boolean isOnlyPronouns(Node entityNode) {
        
        if (entityNode == null) {
            return true;
        }
        
        ArrayList<Node> wordNodes = new ArrayList<>();
        getWordsRecursive(entityNode, wordNodes);
        for (int i = 0; i < wordNodes.size(); i++) {
            if (wordNodes.get(i).getAttributes().getNamedItem("POS") != null && 
                !wordNodes.get(i).getAttributes().getNamedItem("POS").getNodeValue().equals("PRONOUN")) {
                return false;
            }
        }
        return true;
    }
    
    static void printOnlyPronounsChains() throws FileNotFoundException, UnsupportedEncodingException {
        
        Set<Entry<String, ArrayList<String>>> entrySet = heads.entrySet();
        try (PrintWriter printWriter = new PrintWriter("ref_chains_filter_Pronouns.txt", "UTF-8")) {
            
            int suspectChains = 0;
            for (Entry entry : entrySet) {
                String head = (String) entry.getKey();
                ArrayList<String> children = (ArrayList<String>) entry.getValue();

                int nrPronouns = 0;
                
                if (isOnlyPronouns(entityNode.get(head))) {
                    nrPronouns++;
                }
                
                for (int i = 0; i < children.size(); i++) {
                    if (isOnlyPronouns(entityNode.get(children.get(i)))) {
                        nrPronouns++;
                    }
                }
                
                if (nrPronouns == children.size() + 1) {
                    // they're all pronouns
                    
                    suspectChains++;
                        
                    printWriter.printf("\nHEAD     : %s\nCHILDREN : ", prettyString(head));
                    for (int i = 0; i < children.size(); i++) {
                       printWriter.printf("%s ", refRelRef.get(children.get(i)));
                       printWriter.printf("%s  ", prettyString(children.get(i)));
                    }
                    printWriter.println();
                    int sentenceId = Integer.parseInt(head.substring(1)) / Aggregator.SIDMUL;
                    printWriter.printf("SID&ID   : %4d  E%d\n", sentenceId,
                            Integer.parseInt(head.substring(1)) % Aggregator.SIDMUL);

                    printWriter.printf("FILE:    : %s\n", Aggregator.sIdToFile.get(new Integer(sentenceId).toString()));
                }
            }
            printWriter.printf("%5d / %5d suspect chains.\n\n", suspectChains, entrySet.size());
        }
    }
    
    /* 
     * Reads pairs of REFERENTIAL IDS form a manually built file and creates
     * REFERENTIAL TYPE = "coref" relations between those pair of nodes
     */
    static void uniteReferentialChains() throws IOException {
        
        ArrayList<String> toId = new ArrayList<>();
        ArrayList<String> fromId = new ArrayList<>();
        HashMap<Integer, Integer> sIdToLastRefId = new HashMap<>();
        
        // TODO : rest of the code is copied from delete, need to implement the unifications too
        String failIds = "";
        
        int cntAll = 0, cntUnited = 0;
        try (BufferedReader br = new BufferedReader(new FileReader("unite_ref_ids.txt"))) {
            String line;
            System.out.printf("\nAdding REFERENTIAL ids for pairs of enitities: \n");
            while ((line = br.readLine()) != null) {
               
               String[] parts = line.split("\\s+");
               boolean one = false;
               for (int i = 0; i < parts.length; i++) {
                   if (parts[i].length() > 0) {
                       if (!one) {
                           //toId.add(parts[i]);
                           fromId.add(parts[i]);
                           one = true;
                       } else {
                           //fromId.add(parts[i]);
                           toId.add(parts[i]);
                       }
                   }
               }

               /*
               String fromIdStr = fromId.get(fromId.size() - 1);
               String toIdStr = toId.get(toId.size() - 1);
               */
               String toIdStr = fromId.get(fromId.size() - 1);
               String fromIdStr = toId.get(toId.size() - 1);
               
               Node fromNode = entityNode.get(fromIdStr);
               
               if (fromIdStr.length() <= 1 || toIdStr.length() <= 1) {
                   continue;
               }
               cntAll ++;
               if (fromNode != null && entityNode.get(toIdStr) != null) {
                   Element refNode = domDocument.createElement("REFERENTIAL");
                   NodeList childNodes = fromNode.getChildNodes();
                   Node sentenceNode = fromNode;
                   for (int i = 0; i < childNodes.getLength(); i++) {
                       Node childNode = childNodes.item(i);
                       refNode.appendChild(childNode.cloneNode(true));
                   }
                   while (fromNode.getChildNodes().getLength() > 0) {
                       fromNode.removeChild(fromNode.getChildNodes().item(0));
                   }
                   
                   while (sentenceNode != null && !"S".equals(sentenceNode.getNodeName())) {
                       sentenceNode = sentenceNode.getParentNode();
                   }
                   if (sentenceNode == null) {
                       failIds += "(" + fromIdStr + ", " + toIdStr + ") ";
                       continue;
                   }
                   int sId = Integer.parseInt(sentenceNode.getAttributes().getNamedItem("id").getNodeValue());
                   int newRefId = 0;
                   if (sIdToLastRefId.containsKey(sId)) {
                       newRefId = sIdToLastRefId.get(sId) + 1;
                   }
                   sIdToLastRefId.put(sId, newRefId);
                   /*
                   refNode.setAttribute("FROM", fromIdStr);
                   refNode.setAttribute("TO", toIdStr);
                   */
                   refNode.setAttribute("FROM", toIdStr);
                   refNode.setAttribute("TO", fromIdStr);
                   
                   refNode.setAttribute("TYPE", "coref");
                   Integer newRefNumeric = sId * Aggregator.SIDMUL + newRefId;
                   refNode.setAttribute("ID", "REF" +
                           Aggregator.leading0(newRefNumeric.toString(), 9));
                   fromNode.appendChild(refNode);
                   cntUnited++;
               }  else {
                   failIds += "(" + fromIdStr + ", " + toIdStr + ") ";
               }
            }  
            System.out.printf("Could not add the (from, to) pairs : %s\n", failIds);
            System.out.printf("United %d out of %d pairs, the rest of ids could not be found.\n\n",
                    cntUnited, cntAll);
        }
    }
}
