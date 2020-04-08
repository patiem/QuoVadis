package corefgraph;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Graph {
    
    public static Document domDocument;
    public static HashSet<String> entitySet;
    
    public static HashMap<String, ArrayList<String>> corefEdge;
    public static HashMap<String, ArrayList<String>> corefEdgeRef;
    
    // build initial graph edges, entity set, word mappings
    // and print information about duplicate edges
    public static void buildInitialGraph() throws Exception {
        domDocument = CorefGraph.domDocument;
        entitySet = new HashSet<>();
        
        NodeList eList = domDocument.getElementsByTagName("ENTITY");

        for (int i = 0; i < eList.getLength(); i++) {
            Node eNode = eList.item(i);
            String id = eNode.getAttributes().getNamedItem("ID").getNodeValue();
            entitySet.add(id);
        }
        CorefGraph.printTime(); System.out.printf("Added %d entities to the set.\n", entitySet.size());
        
        WordsMapping.buildEntityToWords();
        CorefGraph.printTime(); System.out.printf("Build entity -> words mapping, size = %d.\n", WordsMapping.entityToWords.size());
        
        corefEdge = new HashMap<>();
        corefEdgeRef = new HashMap<>();
        NodeList rList = domDocument.getElementsByTagName("REFERENTIAL");
        for (int i = 0; i < rList.getLength(); i++) {
            Node rNode = rList.item(i);
            if (rNode.getAttributes().getNamedItem("TYPE").getNodeValue().startsWith("coref")) {
                String fromId = rNode.getAttributes().getNamedItem("FROM").getNodeValue();
                String toId = rNode.getAttributes().getNamedItem("TO").getNodeValue();
                                
                if (corefEdge.get(fromId) == null) {
                    ArrayList<String> list = new ArrayList<>();
                    ArrayList<String> list2 = new ArrayList<>();
                    list.add(toId);
                    list2.add(rNode.getAttributes().getNamedItem("ID").getNodeValue());
                    corefEdge.put(fromId, list);
                    corefEdgeRef.put(fromId, list2);
                } else {
                    corefEdge.get(fromId).add(toId);
                    corefEdgeRef.get(fromId).add(rNode.getAttributes().getNamedItem("ID").getNodeValue());
                }
            }
        }
        CorefGraph.printTime(); System.out.printf("Parsed %d referential nodes.\n", rList.getLength());
        
        WordsMapping.buildSentenceToWordsMapping();
        CorefGraph.printTime(); System.out.printf("Build sentence -> words mapping, size = %d.\n", WordsMapping.sentenceIdToWords.size());
        
        SentenceMapping.buildEntityToSentenceIdMapping();
        CorefGraph.printTime(); System.out.printf("Build entity -> sentence mapping, size = %d.\n", WordsMapping.entityToWords.size());
        
        PrintWriter printWriter = new PrintWriter("duplicate_edges.txt", "UTF-8");
        PrintWriter printWriterFrom = new PrintWriter("multiple_from_edges.txt", "UTF-8");
        
        int cnt = 0;
        for (String fromId : corefEdge.keySet()) {
            ArrayList<String> toIds = corefEdge.get(fromId);
            ArrayList<String> refIds = corefEdgeRef.get(fromId);
            if (toIds.size() > 1 && fromId.length() > 1) {
                cnt ++;
                printWriterFrom.printf("%4d  %s -> \n", cnt, prettyEntity(fromId));
                HashSet<String> tmpSet = new HashSet<>();
                int distinct = 0; // first check if there are multiple distinct edges
                for (int i = 0; i < toIds.size(); i++) {
                    if (tmpSet.contains(toIds.get(i))) {
                    } else {
                        distinct ++;
                        tmpSet.add(toIds.get(i));
                    }
                }
                tmpSet = new HashSet<>();
                if (distinct > 1) {
                    for (int i = 0; i < toIds.size(); i++) {
                        if (tmpSet.contains(toIds.get(i))) {
                            printWriter.printf("%s\n", toIds.get(i));
                        } else {
                            printWriterFrom.printf("     %s %s\n", refIds.get(i), prettyEntity(toIds.get(i)));
                            tmpSet.add(toIds.get(i));
                        }
                    }
                }
                printWriterFrom.println();
            }
        }
        
        printWriter.close();
        printWriterFrom.close();
    }
    
    public static String prettyEntity(String entity) {
        String rez = entity;
        rez += " [" + WordsMapping.entityToWords.get(entity) + "] ";
        rez += "S" + SentenceMapping.entityToSId.get(entity);
        rez += " {" + WordsMapping.sentenceIdToWords.get(SentenceMapping.entityToSId.get(entity)) + "} ";
        return rez;
    }
    
    public static void cycleDetection() {
        CorefGraph.printTime(); System.out.printf("Running cycle detection.\n");
        HashMap<String, String> bfsPath; // path pointers for the bfs
        HashMap<String, String> bfsPathRef; // edge relations
        HashSet<String> visited = new HashSet<>(); // if already in some cycle

        int cntParsed = 0;
        for (String entity : entitySet) {
            
            cntParsed++;
            //if (cntParsed % 100 == 0) {
                //CorefGraph.printTime(); System.out.printf("Parsed %5d / %5d enitties for cycle detection.\n", cntParsed, entitySet.size());
            //}
            
            if (visited.contains(entity)) {
                continue;
            }
            
            // stat from each 'entity' node
            bfsPath = new HashMap();
            bfsPathRef = new HashMap();
            int in = 0;
            ArrayList<String> queue = new ArrayList<>();
            queue.add(entity);
            
            while (in < queue.size()) {
                String ent = queue.get(in);
                if (!corefEdge.containsKey(ent)) {
                    in++;
                    continue;
                }
                String toEnt = corefEdge.get(ent).get(0);
                if (toEnt == null) {
                    continue;
                }
                // ent -> toEnt

                if (bfsPath.containsKey(toEnt)) {
                    // cycle !
                    if (!visited.contains(toEnt)) {
                        CorefGraph.printTime();
                        System.out.printf("cycle : ");
                        String itr = toEnt;
                        while (itr != null && bfsPathRef.get(itr) != null &&
                               !visited.contains(itr)) { // !itr.equals(ent)) {
                            System.out.printf("%s ---%s--> ", itr, bfsPathRef.get(itr));
                            visited.add(itr);
                            itr = bfsPath.get(itr);
                        }
                        visited.add(ent);
                        System.out.printf("%s ---%s--> %s\n",
                                ent, corefEdgeRef.get(ent).get(0), toEnt);
                        visited.add(ent);
                    }
                } else {
                    bfsPath.put(ent, toEnt);
                    if (ent.equals("E360600030") && toEnt.equals("E360600032")) {
                        int a = 3 + 2;
                    }
                    bfsPathRef.put(ent, corefEdgeRef.get(ent).get(0));
                    queue.add(toEnt);
                }
                in++;
            }
        }
    }
}
