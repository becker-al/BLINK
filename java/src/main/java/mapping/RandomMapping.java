package mapping;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import java.util.*;

public class RandomMapping extends MappingAlgorithm{
    @Override
    public Map<String, String> computeMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB) {
        HashMap<String, String> map = new HashMap<>();
        Random random = new Random(1);
        List<String> a = new ArrayList<>(blankNodesA.stream().toList().stream().map(Resource::getURI).toList());
        Collections.shuffle(a, random);
        List<String> b = new ArrayList<>(blankNodesB.stream().toList().stream().map(Resource::getURI).toList());
        Collections.shuffle(b, random);
        for (int i = 0; i < a.size(); i++) {
            map.put(a.get(i), b.get(i));
        }
        return map;
    }
}
