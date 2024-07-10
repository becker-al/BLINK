package error;

import org.apache.jena.rdf.model.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static utils.BNodeUtils.*;

public interface ErrorFunction {

    /**
     * This function computes the delta between two models. The models should not be changed by this function.
     */
    int computeDelta(Model modelA, Model modelB, Map<String, String> givenMapping);

    default void applyMapping(Model model, Map<String, String> givenMapping) {
        Set<Statement> replacements = new HashSet<>();
        for (Statement statement : model.listStatements().toSet()) {
            Resource sub = null;
            Property p = statement.getPredicate();
            RDFNode obj = null;


            if (isBlankNode(statement.getSubject())) {
                String subUri = statement.getSubject().getURI();
                String uri = givenMapping.get(subUri);
                if(uri == null){
                    throw new IllegalArgumentException("No mapping for " + subUri);
                }
                sub = model.createResource(uri);
            } else {
                sub = statement.getSubject();
            }
            if (isBlankNode(statement.getObject())) {
                String objUri = statement.getObject().asResource().getURI();
                String uri = givenMapping.get(objUri);
                if(uri == null){
                    throw new IllegalArgumentException("No mapping for " + objUri);
                }
                obj = model.createResource(uri);
            } else {
                obj = statement.getObject();
            }
            model.remove(statement);

            replacements.add(model.createStatement(sub, p, obj));
        }

        for (Statement replacement : replacements) {
            model.add(replacement);
        }
    }

    default void removeBlankNodePrefixes(Model model) {
        for (Statement statement : model.listStatements().toSet()) {
            Resource sub = null;
            Property p = statement.getPredicate();
            RDFNode obj = null;
            if (isBlankNode(statement.getSubject())) {
                sub = model.createResource(BNODE_PREFIX + shorten(statement.getSubject().getURI()));
            } else {
                sub = statement.getSubject();
            }
            if (isBlankNode(statement.getObject())) {
                obj = model.createResource(BNODE_PREFIX + shorten(statement.getObject().asResource().getURI()));
            } else {
                obj = statement.getObject();
            }
            model.remove(statement);
            model.add(sub, p, obj);
        }
    }


}
