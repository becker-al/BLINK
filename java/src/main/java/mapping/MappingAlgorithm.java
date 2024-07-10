package mapping;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import java.util.Map;
import java.util.Set;

public abstract class MappingAlgorithm {
    public abstract Map<String, String> computeMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB);

}
