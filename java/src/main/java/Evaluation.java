import error.*;
import mapping.*;
import org.apache.jena.rdf.model.*;
import utils.BNodeUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static utils.BNodeUtils.*;

public class Evaluation {

    public static void main(String[] args) {
        //TODO Array of location of training / mapping files. For example C:\\KGs\\Synthetic\\Random-R1-L4\\
        String[] files = {

        };

        ExecutorService executorService = Executors.newFixedThreadPool(14);

        for (String location : files) {
            executorService.submit(() -> {
                try {
                    String outFileLoc = location + "results.txt";
                    BufferedWriter writer = new BufferedWriter(new FileWriter(outFileLoc));
                    evaluateGraph(location, writer);
                    writer.close();
                    System.err.println("Done with " + location);
                }catch (IOException e){
                    throw new RuntimeException(e);
                }
            });
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("done");

    }

    private static void evaluateGraph(String location, BufferedWriter writer) throws IOException {
        Model modelA = ModelFactory.createDefaultModel();
        modelA.read(location + "originalA.txt", "N-TRIPLE");

        Model modelB = ModelFactory.createDefaultModel();
        modelB.read(location + "originalB.txt", "N-TRIPLE");

        //Gets a list of all bnodes from the graphs
        Set<Resource> blankNodesA = extractAllBNodes(modelA);
        Set<Resource> blankNodesB = extractAllBNodes(modelB);


        printWrite(writer, "Total blank nodesA " + blankNodesA.size());
        printWrite(writer, "Total blank nodesB " + blankNodesB.size());

        //Number of nodes that are only connected to other bnodes
        printWrite(writer, "Total bbnodesA " + blankNodesA.stream().filter(resource -> BNodeUtils.computeNeighbours(modelA, resource).size() - BNodeUtils.computeBlankNeighbours(modelA, resource).size() == 0).count());
        printWrite(writer, "Total bbnodesB " + blankNodesB.stream().filter(resource -> BNodeUtils.computeNeighbours(modelB, resource).size() - BNodeUtils.computeBlankNeighbours(modelB, resource).size() == 0).count());

        printWrite(writer, "Total triplesA " + modelA.listStatements().toSet().size());
        printWrite(writer, "Total triplesB " + modelB.listStatements().toSet().size());

        printWrite(writer, "Total blankcomponentsA: " + blankNodesA.stream().map(resource -> BNodeUtils.computeBNodesInBComponent(modelA, resource)).distinct().count());
        printWrite(writer, "Total blankcomponentsB: " + blankNodesB.stream().map(resource -> BNodeUtils.computeBNodesInBComponent(modelB, resource)).distinct().count());

        printWrite(writer, "Total btriplesA " + modelA.listStatements().filterKeep(statement -> isBlankNode(statement.getSubject()) || isBlankNode(statement.getObject())).toSet().size());
        printWrite(writer, "Total btriplesB " + modelB.listStatements().filterKeep(statement -> isBlankNode(statement.getSubject()) || isBlankNode(statement.getObject())).toSet().size());
        printWrite(writer, "Total bbtriplesA " + modelA.listStatements().filterKeep(statement -> isBlankNode(statement.getSubject()) && isBlankNode(statement.getObject())).toSet().size());
        printWrite(writer, "Total bbtriplesB " + modelB.listStatements().filterKeep(statement -> isBlankNode(statement.getSubject()) && isBlankNode(statement.getObject())).toSet().size());

        //Max BlankRadius
        printWrite(writer, "Max radius A " + computeMaxRadiusToNonBlank(modelA));
        printWrite(writer, "Max radius B " + computeMaxRadiusToNonBlank(modelB));


        printWrite(writer, "---------------");
        printWrite(writer, "PerfectMapping");
        evaluateMapping(modelA, modelB, new SelfMapping().computeMapping(modelA, modelA, blankNodesA, blankNodesA), writer);

        printWrite(writer, "NoMapping");
        evaluateMapping(modelA, modelB, new NoMapping().computeMapping(modelA, modelA, blankNodesA, blankNodesA), writer);


        printWrite(writer, "SIGNATURE");
        evaluateMapping(modelA, modelB, new SignatureAlgorithmMapping().computeMapping(modelA, modelB, blankNodesA, blankNodesB), writer);


        int[] radii = new int[]{2,3,1000};
        for (int radius : radii) {
            printWrite(writer, "SIGN-R" + radius);
            evaluateMapping(modelA, modelB, new RSignAlgorithmMapping(radius).computeMapping(modelA, modelB, blankNodesA, blankNodesB), writer);
        }

        //printWrite(writer, "RANDOM");
        //evaluateMapping(modelA, modelB, new RandomMapping().computeMapping(modelA, modelB, blankNodesA, blankNodesB), writer);


        Arrays.stream(new File(location).listFiles()).filter(file -> file.getName().startsWith("mapping")).sorted().forEachOrdered(file -> {
            printWrite(writer, file.getName());
            evaluateMapping(modelA, modelB, new FileMapping(file).computeMapping(modelA, modelB, blankNodesA, blankNodesB), writer);
        });
    }

    /**
     * Prints something to the console and writes it to a writer
     */
    private static void printWrite(BufferedWriter writer, String s){
        System.out.println(s);
        try {
            writer.write(s + "\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void evaluateMapping(Model modelA, Model modelB, Map<String, String> possibleMapping, BufferedWriter writer) {
        printWrite(writer, "URIDiffs: " + new ErrorNumberOfURIDiffs().computeDelta(modelA, modelB, possibleMapping));
        printWrite(writer, "Delta: " + new ErrorNumberOfTripleChanges().computeDelta(modelA, modelB, possibleMapping));
        printWrite(writer, "---");
    }

    /**
     * Checks if the mapping is a 1to1 mapping of all bnodes
     */
    private static void checkValidityOfMapping(Model modelA, Model modelB, Map<String, String> possibleMapping) {
        Set<String> modelANodes = possibleMapping.keySet();
        Set<String> modelBNodes = new HashSet<>(possibleMapping.values());
        Set<String> aBNodes = BNodeUtils.computeBlankNodes(modelA).stream().map(Resource::getURI).collect(Collectors.toSet());
        Set<String> bBNodes = BNodeUtils.computeBlankNodes(modelB).stream().map(Resource::getURI).collect(Collectors.toSet());

        if (!modelANodes.equals(aBNodes)) {
            throw new RuntimeException("The nodes of model A do not correspond to the mapping keys");
        }
        if (!modelBNodes.equals(bBNodes)) {
            throw new RuntimeException("The nodes of model B do not correspond to the mapping values");
        }
    }


}
