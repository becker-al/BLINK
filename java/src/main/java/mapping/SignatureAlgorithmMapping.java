package mapping;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import signatures.Signature;
import signatures.SignatureAlgorithm;

import java.util.*;
import java.util.stream.Collectors;

import static utils.BNodeUtils.signDiff;

public class SignatureAlgorithmMapping extends MappingAlgorithm {

    private final SignatureAlgorithm signatureAlgorithm = new Signature();

    @Override
    public Map<String, String> computeMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB) {
        return signMapping(modelA, modelB, blankNodesA, blankNodesB);
    }

    private Map<String, String> signMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB) {
        Set<String> blankAUris = blankNodesA.stream().map(Resource::getURI).collect(Collectors.toSet());
        Set<String> blankBUris = blankNodesB.stream().map(Resource::getURI).collect(Collectors.toSet());

        Map<String, String> signatures = new HashMap<>();
        createSignatures(modelA, blankNodesA, signatures);
        createSignatures(modelB, blankNodesB, signatures);

        Map<String, String> mapping = new HashMap<>();

        //Exact matches
        for (String bA : blankAUris) {
            String signatureA = signatures.get(bA);
            Optional<String> optionalMatch = signatures.entrySet().stream()
                    .filter(stringStringEntry -> blankBUris.contains(stringStringEntry.getKey())) //Only check B's
                    .filter(bEntry -> signatureA.equals(bEntry.getValue())) //Check if signatures are equal
                    .map(Map.Entry::getKey) //Map to the uri of the node from set B
                    .findFirst();
            if (optionalMatch.isPresent()) {
                String bB = optionalMatch.get();
                mapping.put(bA, bB);
                blankBUris.remove(bB); //Remove matched B from the set
            }
        }
        mapping.keySet().forEach(blankAUris::remove); //Remove matched As from the set

        //Closest matches
        //THE PAPER COMPARES SIGNATURES WITH SOME BINARY STUFF, BUT THIS IS NOT DONE HERE
        for (String bA : blankAUris) {
            Optional<String> optionalMatch = signatures.entrySet().stream()
                    .filter(stringStringEntry -> blankBUris.contains(stringStringEntry.getKey())) //Only check B's
                    .sorted(Comparator.comparingInt(o -> signDiff(signatures.get(bA), o.getValue())))
                    .map(Map.Entry::getKey) //Map to the uri of the node from set B
                    .findFirst();
            if (optionalMatch.isPresent()) { //Still use optional as there may be more nodes in A than in B
                String bB = optionalMatch.get();
                mapping.put(bA, bB);
                blankBUris.remove(bB); //Remove matched B from the set
            }else{
                throw new IllegalArgumentException("There should be a match for everything");
            }
        }

        return mapping;
    }

    private void createSignatures(Model model, Set<Resource> blankNodes, Map<String, String> signatures) {
        for (Resource bA : blankNodes) {
            signatures.put(bA.getURI(), signatureAlgorithm.createSignature(model, bA));
        }
    }


}
