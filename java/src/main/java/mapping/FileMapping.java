package mapping;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.io.File;

public class FileMapping extends MappingAlgorithm{

    File file;

    public FileMapping(File file) {
        this.file = file;
    }

    @Override
    public Map<String, String> computeMapping(Model modelA, Model modelB, Set<Resource> blankNodesA, Set<Resource> blankNodesB) {
        //Map<String, String> mapping = new NoMapping().computeMapping(modelA, modelB, blankNodesA, blankNodesB); //Start with no mapping but all bAs already in the map

        Map<String, String> mapping = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] split = line.split(">-<");
                String keyA = split[0].replaceAll("<", "").replaceAll(">", "");
                String keyB = split[1].replaceAll("<", "").replaceAll(">", "");
                mapping.put(keyA, keyB);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return mapping;
    }
}
