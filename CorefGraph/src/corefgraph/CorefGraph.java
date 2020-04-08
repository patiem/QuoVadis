package corefgraph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CorefGraph {

    public static DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    public static DocumentBuilder documentBuilder;
    public static Document domDocument;
    private static File xmlInputFile;
    
    public static void main(String[] args) throws Exception {
        
        documentBuilder = documentBuilderFactory.newDocumentBuilder();        
        xmlInputFile = new File("QuoVadis_v0.xml");
        domDocument = documentBuilder.parse(xmlInputFile);
        
        Graph.buildInitialGraph();
        Graph.cycleDetection();
    }
    
    static long initialMilis = System.currentTimeMillis();
    public static void printTime() {
        System.out.printf("[%3d s] ", (System.currentTimeMillis() - initialMilis) / 1000);
    }
    
    public static String gA(Node node, String attribute) {
        return node.getAttributes().getNamedItem(attribute).getNodeValue();
    }
}
