package mapping;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import utils.BNodeUtils;

import java.util.*;

public class SelfMapping extends MappingAlgorithm{
    @Override
    public Map<String, String> computeMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB) {
        HashMap<String, String> map = new HashMap<>();
        for (Resource resource : blankNodesA) {
            map.put(resource.getURI(), BNodeUtils.BNODE_PREFIX_B + BNodeUtils.shorten(resource.getURI()));
        }
        return map;
    }
}
