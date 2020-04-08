/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package aggregator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author paul
 */
public class NamesResolution {

    // E029045 -> "Nero"
    public static HashMap<String, String> entityToName;
    
    // "Nero", "Vinicius", etc
    public static HashSet<String> nameHeads;
    
    public static HashMap<String, HashSet<String>> entityToWords;
    // entity id to direct words under that ENTITY XML node
    public static HashMap<String, HashSet<String>> entityToChainWords;
    // entity id to all words under XML nodes of Entities in the same ref chain

    public static Pattern entityIdPattern = Pattern.compile("E(\\d+)");
    public static boolean isEntityId(String candidate) {
        return entityIdPattern.matcher(candidate).find();
    }
    
    public static HashMap<String, HashSet<String>> nameClasses;
    public static void readNameClasses() throws FileNotFoundException, IOException {
        nameClasses = new HashMap<>();
        
        File file = new File("name_classes.txt");
        FileReader reader = new FileReader(file);
        BufferedReader buffReader = new BufferedReader(reader);

        String line;
        while((line = buffReader.readLine()) != null) {
            
            String nameHead = null;
            StringTokenizer tokens = new StringTokenizer(line, " ", true);
            while (tokens.hasMoreTokens()) {
                String token = tokens.nextToken();
                while (token.length() > 0 && !Aggregator.isLetter(token.charAt(0))) {
                    token = token.substring(1);                    
                }
                if (nameHead == null) {
                    nameHead = token;
                    nameClasses.put(nameHead, new HashSet<String>());
                }
                nameClasses.get(nameHead).add(token);
            }
        }
    }
    
    public static void printNameResolutionStats() {
        System.out.printf("Mapped %6d entities to characters.\n", entityToName.size());
        System.out.printf("Got    %6d character names.\n", nameHeads.size());
    }
    
    public static void addWordsRec(Node node, HashSet<String> words) {
        if (node.getNodeName().equals("W")) {
            words.add(node.getTextContent());
        } else {
            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                addWordsRec(node.getChildNodes().item(i), words);
            }
        }
    }
    
    public static void buildEntityToWords() {
        entityToWords = new HashMap<>();
        NodeList eList = Aggregator.aggregatedDomDocument.getElementsByTagName("ENTITY");        
        
        System.out.printf("Building entity to words mapping.\n");
        for (int i = 0; i < eList.getLength(); i++) {
            Node eNode = eList.item(i);            
            HashSet<String> words = new HashSet<>();
            addWordsRec(eNode, words);
            entityToWords.put(eNode.getAttributes().getNamedItem("ID").getNodeValue(), words);
            if (i % 5000 == 0) {
                System.out.printf("Mapped %6d / %6d entities to words.\n", i, eList.getLength());
            }
        }
        System.out.printf("Done mapping entity ids to direct words map has %d entries.\n", entityToWords.size());
    }
    
    public static void readNamesFromRefUnification() throws FileNotFoundException, IOException {
        
        entityToName = new HashMap<>();
        nameHeads = new HashSet<>();
        String prevName = "none";
        
        File file = new File("names_unite.txt");
        FileReader reader = new FileReader(file);
        BufferedReader buffReader = new BufferedReader(reader);

        String line;
        while((line = buffReader.readLine()) != null) {
            
            StringTokenizer tokens = new StringTokenizer(line, " ", true);
            while (tokens.hasMoreTokens()) {
                String token = tokens.nextToken();
                
                while (token.length() > 0 && !Aggregator.isLetter(token.charAt(0))) {
                    token = token.substring(1);                    
                }
                if (token.length() == 0) {
                    continue;
                }
                
                if (isEntityId(token)) {
                    entityToName.put(token, prevName);
                } else {
                    prevName = token;
                    nameHeads.add(token);
                    //System.out.printf("%s\n", token);
                }
            }
        }
        System.out.printf("\nRead and applied names from unification chains.\n");
        printNameResolutionStats();
    }
    
    public static void addChainInformation() {
        
        // iterates all chains and combines information
        
        Set<String> entityHeadSet = ReferentialChains.heads.keySet();
        int entsCnt = 0;
        for (String entityHead : entityHeadSet) {
            
            ArrayList<String> ents = ReferentialChains.heads.get(entityHead);
            ents = (ArrayList<String>) ents.clone();
            ents.add(entityHead);
            entsCnt += ents.size();
            
            HashMap<String, Integer> appearences = new HashMap<>();
            for (int i = 0; i < ents.size(); i++) {
                String ent = ents.get(i);
                String name = entityToName.get(ent);
                
                if (name != null) {
                    if (appearences.containsKey(name)) {
                        appearences.put(name, appearences.get(name) + 1);
                    } else {
                        appearences.put(name, 1);
                    }
                }
            }
            
            String bestName = "";
            if (appearences.size() > 1) {
                System.out.printf("Got one chain with multiple entity name mappings (head = %s):\n", entityHead);
                for (String name : appearences.keySet()) {
                    System.out.printf("apps[%s] = %s\n", name, appearences.get(name));
                    
                    if (bestName.equals("") ||
                        appearences.get(name) > appearences.get(bestName) ||
                        (appearences.get(name) == appearences.get(bestName) && 
                            name.charAt(0) >= 'A' && name.charAt(0) <= 'Z')) {
                        bestName = name;
                    }
                }
            }
            
            if (!bestName.equals("")) {
                for (int i = 0; i < ents.size(); i++) {
                    entityToName.put(ents.get(i), bestName);
                }
            }
        }
        System.out.printf("\nAdded %d chains information.\n", ReferentialChains.heads.size());
        System.out.printf("Chains have %d total entities.\n", entsCnt);
        printNameResolutionStats();
    }
    
    public static void buildEntityToChainWords() {
        entityToChainWords = new HashMap<>();
        System.out.printf("\nBuilding entity to chain words.\n");
        Set<String> entityHeadSet = ReferentialChains.heads.keySet();
        int cnt = 0;
        for (String entityHead : entityHeadSet) {
            ArrayList<String> ents = ReferentialChains.heads.get(entityHead);
            ents = (ArrayList<String>) ents.clone();
            ents.add(entityHead);
            
            HashSet<String> chainWords = new HashSet<>();
            
            for (int i = 0; i < ents.size(); i++) {
                String ent = ents.get(i);
                if (!entityToWords.containsKey(ent)) {
                    System.out.printf("Could not find entity to words mapping for entity = %s.\n", ent);
                } else {
                    for (String word : entityToWords.get(ent)) {
                        chainWords.add(word);
                    }
                }
            }
            for (int i = 0; i < ents.size(); i++) {
                entityToChainWords.put(ents.get(i), chainWords);
            }
            
            cnt++;
            if (cnt % 500 == 0) {
                System.out.printf("Build mapping for %4d / %4d chains.\n", cnt, entityHeadSet.size());
            }
        }
        System.out.printf("Done building entity to chain words.\n");
    }
    
    public static void buildBasedOnNameClassesAndChains() {

        System.out.printf("\nBuilding based on name classes and chains.\n");
        System.out.printf("Entity to name size = %d.\n", entityToName.size());
        
        NodeList eNodes = Aggregator.aggregatedDomDocument.getElementsByTagName("ENTITY");
        for (int i = 0; i < eNodes.getLength(); i++) {
            
            Node eNode = eNodes.item(i);
            String entityId = eNode.getAttributes().getNamedItem("ID").getNodeValue();
            
            if (entityToName.containsKey(entityId)) {
                continue;
            }
            
            HashSet<String> direct = entityToWords.get(entityId);
            HashSet<String> chain = entityToChainWords.get(entityId);
            
            String bestName = "";
            int bestNameScore = 0;

            for (String nameHead : nameClasses.keySet()) {
                HashSet<String> nameVariations = nameClasses.get(nameHead);
                int cScore = 0;
                    
                for (String word : direct) {
                    if (nameVariations.contains(word)) {
                        cScore += 5;
                    }
                }
                if (chain != null) {
                    for (String word : chain) {
                        if (nameVariations.contains(word)) {
                            cScore ++;
                        }
                    }
                }
                    
                if (bestNameScore < cScore) {
                    bestNameScore = cScore;
                    bestName = nameHead;
                }
            }
            
            if (bestNameScore > 0) {
                entityToName.put(entityId, bestName);
            }
            
            if (i % 5000 == 0) {
                System.out.printf("Build %6d / %6d entities.\n", i, eNodes.getLength());
            }
        }
        
        System.out.printf("Entity to name size = %d.\n\n", entityToName.size());
    }
    
    public static void printEntitiyNameMapping() throws FileNotFoundException, UnsupportedEncodingException {
        
        try (PrintWriter printWriter = new PrintWriter("entity_name_mapping.txt", "UTF-8")) {
            
            int cntNullWords = 0;
            printWriter.printf("Entity -> Name  mapping size = %d\n", entityToName.size());
            printWriter.printf("Entity -> Words mapping size = %d\n", entityToWords.size());
            for (String entity : entityToName.keySet()) {
                printWriter.printf("%10s %15s   ", entity, entityToName.get(entity));
                HashSet<String> words = entityToWords.get(entity);
                if (entityToWords.get(entity) == null) {
                    cntNullWords++;
                    printWriter.println();
                    continue;
                }
                for (String word : entityToWords.get(entity)) {                    
                    if (!isEntityId(word)) {
                        printWriter.printf("%s ", word);
                    }
                }
                printWriter.println();
            }
            printWriter.printf("\n\nThere are %d empty words entities (?)", cntNullWords);
        }
    }
}
