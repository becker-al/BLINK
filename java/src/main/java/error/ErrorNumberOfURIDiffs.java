package error;

import org.apache.jena.rdf.model.Model;

import java.util.Map;

import static utils.BNodeUtils.shorten;

public class ErrorNumberOfURIDiffs implements ErrorFunction{

    @Override
    public int computeDelta(Model modelA, Model modelB, Map<String, String> givenMapping) {
        int count = 0;
        for (Map.Entry<String, String> entry : givenMapping.entrySet()) {
            if(!shorten(entry.getKey()).equals(shorten(entry.getValue()))){
                count++;
            }
        }
        return count;
    }

}
