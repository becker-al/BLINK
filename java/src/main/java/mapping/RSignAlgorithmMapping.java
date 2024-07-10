package mapping;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import signatures.SignBN;
import signatures.Signature;
import utils.BNodeUtils;

import java.util.*;
import java.util.stream.Collectors;

import static utils.BNodeUtils.signDiff;

public class RSignAlgorithmMapping extends MappingAlgorithm {

    private final int radius;
    private SignBN signBN_A;
    private SignBN signBN_B;


    public RSignAlgorithmMapping(int radius) {
        this.radius = radius;
    }

    public void init(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB){
        signBN_A = new SignBN(modelA);
        signBN_B = new SignBN(modelB);

        Map<String, String> signatures = new HashMap<>();
        createSignSignatures(modelA, blankNodesA, signatures);
        createSignSignatures(modelB, blankNodesB, signatures);
    }

    @Override
    public Map<String, String> computeMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB) {
        signBN_A = new SignBN(modelA);
        signBN_B = new SignBN(modelB);

        Map<String, String> signatures = new HashMap<>();
        createSignSignatures(modelA, blankNodesA, signatures);
        createSignSignatures(modelB, blankNodesB, signatures);

        return rSignMapping(modelA, modelB, blankNodesA, blankNodesB, signatures, radius);
    }


    protected Map<String, String> rSignMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB, Map<String, String> signatures, int radius) {
        Set<String> blankAUris = blankNodesA.stream().map(Resource::getURI).collect(Collectors.toSet());
        Set<String> blankBUris = blankNodesB.stream().map(Resource::getURI).collect(Collectors.toSet());

        Map<String, String> mapping = new HashMap<>();

        Map<String, Set<String>> mExact = new HashMap<>();
        Map<Integer, Set<Pair<String, String>>> mBest = new HashMap<>();
        for (String bA : blankAUris) {
            mExact.put(bA, new HashSet<>());
        }
        for (int i = 1; i <= radius; i++) {
            mBest.put(i, new HashSet<>());
        }

        Set<String> helperVisitedA = new HashSet<>();        //Exact matches

        for (String bA : blankAUris) {
            if (helperVisitedA.contains(bA)) {
                continue; //To simulate line 13
            }
            String signatureA = signatures.get(bA);
            Set<String> bBs = signatures.entrySet().stream()
                    .filter(stringStringEntry -> blankBUris.contains(stringStringEntry.getKey())) //Only check B's
                    .filter(bEntry -> signatureA.equals(bEntry.getValue())) //Check if signatures are equal
                    .map(Map.Entry::getKey) //Map to the uri of the node from set B
                    .collect(Collectors.toSet());
            loop:
            for (String bB : bBs) {
                List<Pair<Resource, Resource>> toBeVisited = new LinkedList<>();
                toBeVisited.add(new Pair<>(modelA.getResource(bA), modelB.getResource(bB)));
                List<Pair<Resource, Resource>> visited = new LinkedList<>();
                String type = findMatch(toBeVisited, visited, 2, radius, signatures);
                switch (type) {
                    case "BComponent-R-Match":
                        visited.stream().map(resourceResourcePair ->
                                        new Pair<>(resourceResourcePair.getLeft().asResource().getURI(), resourceResourcePair.getRight().asResource().getURI()))
                                .forEach(stringStringPair -> {
                                    mapping.put(stringStringPair.getLeft(), stringStringPair.getRight());
                                    blankBUris.remove(stringStringPair.getRight());
                                });
                        helperVisitedA.addAll(visited.stream().map(resourceResourcePair -> resourceResourcePair.getLeft().asResource().getURI()).collect(Collectors.toSet()));
                        break loop;
                    case "M-Exact":
                        mExact.get(bA).add(bB);
                        break;
                    default: //M-Best
                        String s = type.split("_")[1];
                        int rDashResult = Integer.parseInt(s);
                        mBest.get(rDashResult).add(new Pair<>(bA, bB));
                        break;
                }
            }
        }
        helperVisitedA.forEach(blankAUris::remove); //Remove matched As from the set - Originally done in line 13

        Set<String> copyA = new HashSet<>(blankAUris);
        Set<String> copyB = new HashSet<>(blankBUris);
        copyA = copyA.stream().map(BNodeUtils::shorten).collect(Collectors.toSet());
        copyB = copyB.stream().map(BNodeUtils::shorten).collect(Collectors.toSet());
        if (!copyA.equals(copyB)) {
            //throw new RuntimeException("If this fires in the isomorphism task, there is an error in the code. Uncomment this line if the differential task is done");
        }

        //Line20
        for (Map.Entry<String, Set<String>> entry : mExact.entrySet()) {
            String bA = entry.getKey();
            for (String bB : entry.getValue()) {
                if (blankAUris.contains(bA) && blankBUris.contains(bB)) {
                    mapping.put(bA, bB);
                    blankAUris.remove(bA);
                    blankBUris.remove(bB);
                }
            }
        }

        //Line24
        for (int rDash = radius - 1; rDash >= 1; rDash--) {
            for (Pair<String, String> pair : mBest.get(rDash)) {
                String bA = pair.getLeft();
                String bB = pair.getRight();
                if (blankAUris.contains(bA) && blankBUris.contains(bB)) {
                    mapping.put(bA, bB);
                    blankAUris.remove(bA);
                    blankBUris.remove(bB);
                }
            }
        }

        //Line29
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
            }
        }

        return mapping;
    }

    protected String findMatch(List<Pair<Resource, Resource>> toBeVisited, List<Pair<Resource, Resource>> visited, int rDash, int radius, Map<String, String> signatures) {
        boolean equivDNGs = true;
        List<Pair<Resource, Resource>> tmp = new LinkedList<>();

        //Line 3
        while (!toBeVisited.isEmpty()) {
            //Line 4
            Pair<Resource, Resource> bPair = toBeVisited.get(0);
            toBeVisited.remove(0);
            Resource bi = bPair.getLeft();
            Resource bj = bPair.getRight();
            //Line 5
            for (int k = 0; k < signBN_A.signBN(bi, signatures).size(); k++) {
                //Line 6
                Resource ai = signBN_A.signBN(bi, signatures).get(k);
                Resource aj = signBN_B.signBN(bj, signatures).get(k);
                Pair<Resource, Resource> aPair = new Pair<>(ai, aj);
                //Line 7
                if (signatures.get(ai.getURI()).equals(signatures.get(aj.getURI()))) {
                    //Line 8
                    if (!visited.contains(aPair) && !toBeVisited.contains(aPair) && !tmp.contains(aPair)) {
                        //Line 9
                        tmp.add(aPair);
                    }
                }
                //Line 10
                else {
                    //Line 11
                    equivDNGs = false;
                }
            }
            //Line 12
            visited.add(bPair);
        }

        //Line 13
        toBeVisited = tmp;

        if (equivDNGs) {
            if (rDash == radius && toBeVisited.size() > 0) {
                return "M-Exact";
            } else {
                if (toBeVisited.size() > 0) {
                    return findMatch(toBeVisited, visited, rDash + 1, radius, signatures);
                } else {
                    return "BComponent-R-Match";
                }
            }
        } else {
            return "M-Best_" + (rDash-1);
        }
    }

    protected void createSignSignatures(Model model, Set<Resource> blankNodes, Map<String, String> signatures) {
        Signature signature = new Signature();
        for (Resource bA : blankNodes) {
            signatures.put(bA.getURI(), signature.createSignature(model, bA));
        }
    }

}
