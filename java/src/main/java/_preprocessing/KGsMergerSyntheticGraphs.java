package _preprocessing;

import org.apache.jena.rdf.model.*;
import utils.FileUtils;

import java.io.*;
import java.util.*;

import static utils.BNodeUtils.*;

public class KGsMergerSyntheticGraphs {

    //TODO The location where the resulting training files should be saved. For example C:\\KGs\\Synthetic\\
    private static final String outPath = "<DIRECTORY FOR OUTPUT TRAINING FILES>";

    public static void main(String[] args) {
        int linksPerNode = 4;
        for (int radius = 1; radius < 8; radius++) {
            Model model = createGraph(radius, linksPerNode);
            mergeGraphs(model, "Random-R" + radius + "-L" + linksPerNode);
        }
    }

    private static Model createGraph(int radius, int linksPerNode) {
        Model model = ModelFactory.createDefaultModel();
        Random random = new Random(1);
        Map<Integer, List<Resource>> radiusResources = new HashMap<>();

        //Create random resources
        for (int i = 1; i <= radius; i++) {
            ArrayList<Resource> nodes = new ArrayList<>();
            radiusResources.put(i, nodes);
            for (int j = 0; j < Math.pow(linksPerNode, i-1); j++) {
                nodes.add(createRandomBlank(model, random));
            }
        }
        List<Resource> namedOuterRing = new ArrayList<>();
        for (int i = 0; i < Math.pow(linksPerNode, radius); i++) {
            namedOuterRing.add(createRandomResource(model, random));
        }
        radiusResources.put(radius+1, namedOuterRing);

        //Create random links with the previous layer
        for (int i = 2; i <= radius+1; i++) {
            List<Resource> current = radiusResources.get(i);
            List<Resource> previous = radiusResources.get(i - 1);
            for (Resource resource : current) {
                for (int links = 0; links < linksPerNode; links++) {
                    //Creates a statement with a node from the previous layer as subject, a random predicate and the predefined resource as object
                    Statement statement = model.createStatement(previous.get(random.nextInt(previous.size())), createRandomProperty(model, random), resource);
                    model.add(statement);
                }
            }
        }

        return model;
    }

    private static Resource createRandomBlank(Model model, Random random) {
        return model.createResource(new AnonId(generateRandomString(random, 32)));
    }

    private static Resource createRandomResource(Model model, Random random) {
        return model.createResource("http://" + generateRandomString(random, 20) + ".org/" + generateRandomString(random, 20));
    }


    private static Property createRandomProperty(Model model, Random random) {
        return model.createProperty("http://prop.org/" + generateRandomString(random, 2));
    }


    private static void mergeGraphs(Model model, String outDirectoryName) {
        Model modelA = copyModelKeepName(model, BNODE_PREFIX_A);
        Model modelB = copyModelKeepName(model, BNODE_PREFIX_B);

        new File(outPath + outDirectoryName.replaceAll(".nt", "")).mkdirs();
        try {
            new File(outPath).mkdirs();
            modelA.write(new FileWriter(outPath + outDirectoryName.replaceAll(".nt", "") + "\\originalA.txt"), "N-TRIPLE");
            String bPath = outPath + outDirectoryName.replaceAll(".nt", "") + "\\originalB.txt";
            modelB.write(new FileWriter(bPath), "N-TRIPLE");
            List<String> lines = FileUtils.readLinesFromFile(bPath);
            Collections.reverse(lines);
            FileUtils.writeLinesToFile(bPath, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Model merged = mergeModels(modelA, modelB);

        try {
            merged.write(new FileWriter(outPath + outDirectoryName.replaceAll(".nt", "") + "\\train.txt"), "N-TRIPLE");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Wrote: " + outDirectoryName);
    }

    public static String generateRandomString(Random random, int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(characters.length());
            char randomChar = characters.charAt(randomIndex);
            stringBuilder.append(randomChar);
        }

        return stringBuilder.toString();
    }
    public static Model mergeModels(Model model1, Model model2) {
        Model merged = ModelFactory.createDefaultModel();
        model1.listStatements().forEachRemaining(merged::add);
        model2.listStatements().forEachRemaining(merged::add);
        return merged;
    }

    private static Model copyModelKeepName(Model model, String blankNodePrefix) {
        Map<AnonId, Resource> idMapping = new HashMap<>();
        Model copy = ModelFactory.createDefaultModel();
        List<Statement> statementList = model.listStatements().toList();
        statementList.forEach(statement -> {
            if (!statement.getSubject().isAnon() && !statement.getObject().isAnon()) {
                copy.add(statement);
            } else if (statement.getSubject().isAnon() && statement.getObject().isAnon()) {
                if (!idMapping.containsKey(statement.getSubject().getId())) {
                    idMapping.put(statement.getSubject().getId(), copy.createResource((blankNodePrefix + statement.getSubject().toString())));
                }
                if (!idMapping.containsKey(statement.getObject().asResource().getId())) {
                    idMapping.put(statement.getObject().asResource().getId(), copy.createResource(blankNodePrefix + statement.getObject().asResource().toString()));
                }
                copy.add(idMapping.get(statement.getSubject().getId()), statement.getPredicate(), idMapping.get(statement.getObject().asResource().getId()));
            } else if (statement.getSubject().isAnon()) {
                if (!idMapping.containsKey(statement.getSubject().getId())) {
                    idMapping.put(statement.getSubject().getId(), copy.createResource(blankNodePrefix + statement.getSubject().toString()));
                }
                copy.add(idMapping.get(statement.getSubject().getId()), statement.getPredicate(), statement.getObject());
            } else if (statement.getObject().isAnon()) {
                if (!idMapping.containsKey(statement.getObject().asResource().getId())) {
                    idMapping.put(statement.getObject().asResource().getId(), copy.createResource(blankNodePrefix + statement.getObject().asResource().toString()));
                }
                copy.add(statement.getSubject(), statement.getPredicate(), idMapping.get(statement.getObject().asResource().getId()));
            } else {
                throw new RuntimeException();
            }

        });
        return copy;
    }


}
