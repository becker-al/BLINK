package mapping;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import utils.BNodeUtils;

import java.util.*;

public class NoMapping extends MappingAlgorithm{
    @Override
    public Map<String, String> computeMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB) {
        HashMap<String, String> map = new HashMap<>();
        List<String> a = new ArrayList<>(blankNodesA.stream().toList().stream().map(resource -> resource.getURI()).toList());
        Collections.shuffle(a);
        for (int i = 0; i < a.size(); i++) {
            map.put(a.get(i), BNodeUtils.BNODE_PREFIX + "#"+i);
        }
        return map;
    }
}
