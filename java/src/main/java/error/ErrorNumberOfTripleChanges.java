package error;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;

import java.util.Map;
import static utils.BNodeUtils.*;

public class ErrorNumberOfTripleChanges implements ErrorFunction{

    @Override
    public int computeDelta(Model modelA, Model modelB, Map<String, String> givenMapping) {
        modelA = copyModel(modelA);
        modelB = copyModel(modelB);

        //Apply mappings
        applyMapping(modelA, givenMapping);

        //Remove BlankNode Prefixes
        removeBlankNodePrefixes(modelA);
        removeBlankNodePrefixes(modelB);
        return computeAdditions(modelA, modelB) + computeAdditions(modelB, modelA);
    }

    public int computeAdditionsAndDeletions(Model modelA, Model modelB){
        return computeAdditions(modelA, modelB) + computeAdditions(modelB, modelA);
    }

    private int computeAdditions(Model modelA, Model modelB) {
        int count = 0;
        for (Statement statement : modelA.listStatements().toSet()) {
            if (!modelB.contains(statement)) {
                count++;
            }
        }
        return count;
    }

}
