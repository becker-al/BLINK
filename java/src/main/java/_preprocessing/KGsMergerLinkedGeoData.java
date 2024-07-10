package _preprocessing;

import org.apache.jena.rdf.model.*;
import utils.FileUtils;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

import static utils.BNodeUtils.*;

public class KGsMergerLinkedGeoData {

    //TODO The location where the resulting training files should be saved. For example C:\\KGs\\LinkedGeoData\\
    private static final String outPath = "<DIRECTORY FOR OUTPUT TRAINING FILES>";

    public static void main(String[] args) {
        //TODO The location of the nt input file. For example C:\\LinkedGeoData\\2015-11-02-Craft.node.sorted.nt
        String sourceLocation = "<INPUT NT FILE LOCATION>";

        for (double randomTripleCount : new double[]{0.02, 0.04, 0.06, 0.08, 0.1, 0.2, 0.5}){
            String outDirectoryName = "2015-11-02-MilitaryThing.node.sorted.nt-PlusRandomObject" + randomTripleCount;
            mergeGraphs(sourceLocation, outDirectoryName, true, false, false, randomTripleCount);
        }
        for (double randomTripleCount : new double[]{0.01, 0.02, 0.04, 0.06, 0.08, 0.1, 0.2}){
            String outDirectoryName = "2015-11-02-MilitaryThing.node.sorted.nt-PlusKnownObject" + randomTripleCount;
            mergeGraphs(sourceLocation, outDirectoryName, false, true, false, randomTripleCount);
        }
        for (double randomTripleCount : new double[]{0.01, 0.02, 0.04, 0.06, 0.08, 0.1, 0.2}){
            String outDirectoryName = "2015-11-02-MilitaryThing.node.sorted.nt-MinusRandomTriples" + randomTripleCount;
            mergeGraphs(sourceLocation, outDirectoryName, false, false, true, randomTripleCount);
        }

    }

    private static void mergeGraphs(String sourceLocation, String outDirectoryName, boolean addRandomTriples, boolean addKnownTriples, boolean removeTriples, double randomTripleCount) {
        String modelLocation = sourceLocation;
        String processedLocation = sourceLocation + ".p";
        String temp = sourceLocation + ".temp";
        //Remove all except last 30000 lines
        reduceToLastXLines(modelLocation, temp, 30000);
        //Makes unnamed nodes of LinkedGeoData blank
        makeTriplifiedNodesBlank(temp, processedLocation);
        System.out.println("Blank conversion done");

        Model model = ModelFactory.createDefaultModel();
        model.read(processedLocation, "N-TRIPLE");

        Model modelA = copyModelKeepName(model, BNODE_PREFIX_A);
        Model modelB = copyModelKeepName(model, BNODE_PREFIX_B);

        Random random = new Random(1);
        if (addRandomTriples) {
            addRandomTriples(modelB, random, randomTripleCount);
        }
        if (addKnownTriples) {
            addKnownObjectTriples(modelB, random, randomTripleCount);
        }
        if (removeTriples) {
            removeTriples(modelB, random, randomTripleCount);
        }

        createLiteralURIs(modelA);
        createLiteralURIs(modelB);

        new File(outPath + outDirectoryName.replaceAll(".nt", "")).mkdir();
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

    private static void removeTriples(Model model, Random random, double randomRatio) {
        Set<Resource> resources = extractAllResources(model);
        int size = model.listStatements().toSet().size();
        int toRemove = (int) (size * randomRatio);

        List<Statement> list = model.listStatements().toList();
        List<Statement> removeList = new ArrayList<>();
        int bcount = countBNodes(list);
        for (int i = 0; i < toRemove; i++) {
            int index = random.nextInt(list.size());
            Statement statement = list.get(index);
            list.remove(index);
            removeList.add(statement);
            int ncount = countBNodes(list);
            if (ncount != bcount) {
                list.add(statement);
                removeList.remove(statement);
                i--;
            }
        }

        for (Statement statement : removeList) {
            model.remove(statement);
        }

    }

    private static void addKnownObjectTriples(Model model, Random random, double randomRatio) {
        Set<Resource> resources = extractAllResources(model);
        int size = model.listStatements().toSet().size();
        int toAdd = (int) (size * randomRatio);
        List<Resource> resourceList = resources.stream().toList();

        List<RDFNode> rdfNodes = extractAllRDFNodes(model).stream().toList();
        List<Property> propertySet = extractAllPredicates(model).stream().toList();

        for (int i = 0; i < toAdd; i++) {
            Resource subject = resourceList.get(random.nextInt(resourceList.size()));
            Property property = propertySet.get(random.nextInt(propertySet.size()));
            RDFNode object = rdfNodes.get(random.nextInt(rdfNodes.size()));
            model.add(subject, property, object);
        }
    }

    private static void addRandomTriples(Model model, Random random, double randomRatio) {
        Set<Resource> resources = extractAllResources(model);
        int size = model.listStatements().toSet().size();
        int toAdd = (int) (size * randomRatio);
        List<Resource> resourceList = resources.stream().toList();
        for (int i = 0; i < toAdd; i++) {
            Property property = model.createProperty("http://" + generateRandomString(random, random.nextInt(20)) + ".org/" + generateRandomString(random, random.nextInt(20)));
            Resource object = model.createResource("http://" + generateRandomString(random, random.nextInt(20)) + ".org/" + generateRandomString(random, random.nextInt(20)));
            model.add(resourceList.get(random.nextInt(resourceList.size())), property, object);
        }
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


    private static void makeTriplifiedNodesBlank(String modelLocation, String processedLocation) {
        File file = new File(modelLocation);
        try {
            FileReader fileReader = new FileReader(file); // A stream that connects to the text file
            BufferedReader bufferedReader = new BufferedReader(fileReader); // Connect the FileReader to the BufferedReader

            File out = new File(processedLocation);
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(out));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/geometry/(.*?)>", "_:geo_$1"); //two times if sub and obj are of this kind
                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/geometry/(.*?)>", "_:geo_$1"); //two times if sub and obj are of this kind

                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/triplify/(.*?)>", "_:triplify_$1"); //two times if sub and obj are of this kind
                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/triplify/(.*?)>", "_:triplify_$1"); //two times if sub and obj are of this kind

                bufferedWriter.write(line);
                bufferedWriter.newLine();
            }

            bufferedReader.close(); // Close the stream
            bufferedWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void reduceToLastXLines(String modelLocation, String processedLocation, int x) {
        File file = new File(modelLocation);
        try {
            FileReader fileReader = new FileReader(file); // A stream that connects to the text file
            BufferedReader bufferedReader = new BufferedReader(fileReader); // Connect the FileReader to the BufferedReader

            File out = new File(processedLocation);
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(out));

            String line;
            int count = 0;

            List<String> lines = new ArrayList<>();
            while ((line = bufferedReader.readLine()) != null) {
                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/geometry/(.*?)>", "_:geo_$1"); //two times if sub and obj are of this kind
                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/geometry/(.*?)>", "_:geo_$1"); //two times if sub and obj are of this kind

                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/triplify/(.*?)>", "_:triplify_$1"); //two times if sub and obj are of this kind
                line = line.replaceFirst("<http:\\/\\/linkedgeodata\\.org/triplify/(.*?)>", "_:triplify_$1"); //two times if sub and obj are of this kind

                lines.add(line);
                if (lines.size() > x) {
                    lines.remove(0);
                }
            }

            for (String s : lines) {
                bufferedWriter.write(s);
                bufferedWriter.newLine();
            }
            System.out.println("Deleted " + (count - x) + " lines");
            bufferedReader.close(); // Close the stream
            bufferedWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Changes literals to uris that have the value of the literal and a symbol indicating it was a literal before
     *
     * @param model
     */
    private static void createLiteralURIs(Model model) {
        Map<String, Resource> literalMapping = new HashMap<>();
        for (Statement statement : model.listStatements().toSet()) {
            if (statement.getObject().isLiteral()) {
                String lexicalForm = statement.getObject().asLiteral().getLexicalForm();
                if (!literalMapping.containsKey(lexicalForm)) {
                    literalMapping.put(lexicalForm, model.createResource(literalMappingSeparator + URLEncoder.encode(lexicalForm)));
                }
                model.remove(statement);
                model.add(statement.getSubject(), statement.getPredicate(), literalMapping.get(lexicalForm));
            }
        }
    }
    private static void printBlankNodeStatements(Model model) {
        model.listStatements().forEachRemaining(statement -> {
            if (statement.getSubject().getURI().startsWith(BNODE_PREFIX_A)
                    || statement.getSubject().getURI().startsWith(BNODE_PREFIX_B)
                    ||
                    (statement.getObject().isResource() &&
                            (statement.getObject().asResource().getURI().startsWith(BNODE_PREFIX_A)
                                    || statement.getObject().asResource().getURI().startsWith(BNODE_PREFIX_B)))
            ) {
                System.out.println(statement);
            }
        });
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
