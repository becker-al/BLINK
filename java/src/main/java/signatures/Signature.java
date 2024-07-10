package signatures;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.rdf.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static utils.BNodeUtils.*;

public class Signature implements SignatureAlgorithm{

    /**
     * Creates a signature with the SIGNATURE algorithm
     */
    @Override
    public String createSignature(Model model, Resource blankNode) {
        //Get rdf type classes for this node

        String signature = "";
        //Line 2-8
        List<Pair<String, String>>[] types = new List[]{
                computeClassTriples(model, blankNode),
                computeInTriples(model, blankNode),
                computeOutTriples(model, blankNode)
        };

        Set<Pair<String, String>> L = ConcurrentHashMap.newKeySet();

        for (List<Pair<String, String>> type : types) {
            for (Pair<String, String> pair : type) {
                String l = pair.getLeft();
                String bdash = pair.getRight();

                Optional<Pair<String, String>> first = L.stream().filter(tpair -> tpair.getRight().equals(bdash)).findFirst();
                if(first.isPresent()){ //Line 11
                    Pair<String, String> existingPair = first.get();
                    L.remove(existingPair);
                    String x = existingPair.getLeft();
                    L.add(new Pair<>(x + "∗" + l, bdash));
                }else{//Line15
                    if(isBlankNode(bdash)){
                        L.add(new Pair<>(l, bdash));
                    }
                }
            }

            for (Pair<String, String> pair : L) {
                String x = pair.getLeft();
                String bdash = pair.getRight();

                L.remove(pair);
                L.add(new Pair<>(x + "◇", bdash));
            }
        }

        List<Pair<String, String>> LList = L.stream().sorted(Comparator.comparing(Pair::getLeft)).toList();
        for (List<Pair<String, String>> type : types) {
            for (Pair<String, String> pair : type) {
                String l = pair.getLeft();
                String bdash = pair.getRight();
                signature = signature + l;
                if(isBlankNode(bdash)){
                    signature = signature + findIndex(LList, bdash);
                }
                signature = signature + "∗";
            }
            signature = signature + "◇";
        }
        return signature;
    }

    /**
     * Finds the index of the pair with bdash as the right component in the given List
     */
    private int findIndex(List<Pair<String, String>> LList, String bdash){
        for (int i = 0; i < LList.size(); i++) {
            if(LList.get(i).getRight().equals(bdash)){
                return i;
            }
        }
        throw new RuntimeException("Bdash " + bdash + " is not in the list???");
    }

    /**
     * Definition 5
     */
    private String computeLabel(Statement statement, Resource b){
        Resource s = statement.getSubject();
        Property p = statement.getPredicate();
        RDFNode o = statement.getObject();

        if(b.equals(o) && !isBlankNode(s)){
            return p.getURI() + "▲" + nodeToString(s);
        }
        if(b.equals(s) && !isBlankNode(o)){
            return p.getURI() + "▲" + nodeToString(o);
        }
        if((b.equals(o) && isBlankNode(s)) || b.equals(s) && isBlankNode(o)){
            return p.getURI() + "▲" + "⏺";
        }
        return "";
    }

    /**
     * Computes all labels of rdf type triples with this resource
     */
    private List<Pair<String, String>> computeClassTriples(Model model, Resource b){
        Property rdfTypeProperty = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Set<Statement> set = model.listStatements(b, rdfTypeProperty, (RDFNode) null).toSet();
        return set.stream()
                .map(statement -> new Pair<>(computeLabel(statement, b), nodeToString(statement.getObject())))
                .sorted(Comparator.comparing(Pair::getLeft))
                .toList();
    }

    /**
     * Computes all labels incoming triples to this resource
     */
    private List<Pair<String, String>> computeInTriples(Model model, Resource b){
        Set<Statement> set = model.listStatements(null, null, b).toSet();
        return set.stream()
                .map(statement -> new Pair<>(computeLabel(statement, b), nodeToString(statement.getObject())))
                .sorted(Comparator.comparing(Pair::getLeft))
                .toList();
    }

    /**
     * Computes all labels outgoing triples from this resource except for rdf type triples
     */
    private List<Pair<String, String>> computeOutTriples(Model model, Resource b){
        Property rdfTypeProperty = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Set<Statement> set = model.listStatements(b, null, (RDFNode) null).filterDrop(statement -> statement.getPredicate().equals(rdfTypeProperty)).toSet();
        return set.stream()
                .map(statement -> new Pair<>(computeLabel(statement, b), nodeToString(statement.getObject())))
                .sorted(Comparator.comparing(Pair::getLeft))
                .toList();
    }

}
