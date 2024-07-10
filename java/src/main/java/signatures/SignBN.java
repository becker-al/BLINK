package signatures;

import org.apache.jena.rdf.model.*;

import java.util.*;

import static utils.BNodeUtils.*;

public class SignBN {

    private final Model model;
    private final Map<String, List<Resource>> signCache = new HashMap<>();
    private final Signature signature = new Signature();

    public SignBN(Model model) {
        this.model = model;
    }

    public List<Resource> signBN(Resource start) {
        Set<Resource> blankNodes = computeBlankNodes(model);
        //Precompute all normal signatures
        Map<String, String> signatures = new HashMap<>();
        for (Resource bA : blankNodes) {
            signatures.put(bA.getURI(), signature.createSignature(model, bA));
        }
        return signBN(start, signatures);
    }

    public List<Resource> signBN(Resource start, Map<String, String> singleSignatures) {
        if (signCache.containsKey(start.getURI())) {
            return signCache.get(start.getURI());
        }

        Set<Resource> blankNeighbours = computeBlankNeighbours(model, start);
        List<Resource> list = blankNeighbours.stream().sorted(new SignBNComparator(model, singleSignatures)).map(RDFNode::asResource).toList();
        signCache.put(start.getURI(), list);

        return list;
    }

    static class SignBNComparator implements Comparator<Resource> {

        private final Model model;
        private final Map<String, String> singleSignatures;

        public SignBNComparator(Model model, Map<String, String> singleSignatures) {
            this.model = model;
            this.singleSignatures = singleSignatures;
        }

        @Override
        public int compare(Resource a, Resource b) {
            return compare(a, b, new HashSet<>(), new HashSet<>());
        }

        public int compare(Resource a, Resource b, Set<Resource> visitedA, Set<Resource> visitedB) {
            String uriA = a.getURI();
            String uriB = b.getURI();
            int compareTo = singleSignatures.get(uriA).compareTo(singleSignatures.get(uriB));
            if (compareTo != 0) {
                return compareTo;
            } else {
                //We go here if the signatures are equal
                List<Resource> neighboursA = computeBlankNeighbours(model, a).stream()
                        .filter(resource -> !visitedA.contains(resource))
                        .sorted((o1, o2) -> singleSignatures.get(o1.getURI()).compareTo(singleSignatures.get(o2.getURI())))
                        .toList();
                List<Resource> neighboursB = computeBlankNeighbours(model, b).stream()
                        .filter(resource -> !visitedB.contains(resource))
                        .sorted((o1, o2) -> singleSignatures.get(o1.getURI()).compareTo(singleSignatures.get(o2.getURI())))
                        .toList();
                for (int i = 0; i < Math.min(neighboursA.size(), neighboursB.size()); i++) {
                    visitedA.add(neighboursA.get(i));
                    visitedB.add(neighboursB.get(i));
                    //Check signatures of neighbouring blank nodes
                    int ncomp = compare(neighboursA.get(i), neighboursB.get(i), visitedA, visitedB);
                    if (ncomp != 0) {
                        return ncomp;
                    }
                }
                if (neighboursA.size() == neighboursB.size()) {
                    //Recursion did not find any node where the signature differs
                    return 0;
                }
                return neighboursA.size() - neighboursB.size();
            }
        }
    }

}
