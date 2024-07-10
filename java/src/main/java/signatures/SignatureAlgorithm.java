package signatures;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

public interface SignatureAlgorithm {


    String createSignature(Model model, Resource blankNode);

}
