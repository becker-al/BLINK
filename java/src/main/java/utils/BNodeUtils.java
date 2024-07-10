package utils;

import org.apache.jena.rdf.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class BNodeUtils {

    public static final String BNODE_PREFIX = "■";
    public static final String BNODE_PREFIX_A = BNODE_PREFIX + "0■";
    public static final String BNODE_PREFIX_B = BNODE_PREFIX + "1■";
    public static final String literalMappingSeparator = "♞";

    /**
     * Computes a set of all resources which have an incoming or outgoing edge to the given resources
     *
     * @param model The model in which the statements can be found
     * @param node  The node to find the neighbours for
     * @return A set with all neighbours
     */
    public static Set<RDFNode> computeNeighbours(Model model, RDFNode node) {
        Set<RDFNode> neighbours = model.listStatements(null, null, node).toSet().stream().map(Statement::getSubject).collect(Collectors.toSet());
        if (node.isResource()) {
            Set<RDFNode> outgoing = model.listStatements(node.asResource(), null, (RDFNode) null).toSet().stream().map(Statement::getObject).collect(Collectors.toSet());
            neighbours.addAll(outgoing);
        }
        return neighbours;
    }

    /**
     * Computes a set of all BLANK NODES which have an incoming or outgoing edge to the given resources
     *
     * @param model The model in which the statements can be found
     * @param node  The node to find the neighbours for
     * @return A set with all BLANK NODE neighbours
     */
    public static Set<Resource> computeBlankNeighbours(Model model, RDFNode node) {
        return computeNeighbours(model, node).stream().filter(BNodeUtils::isBlankNode).map(RDFNode::asResource).collect(Collectors.toSet());
    }

    /**
     * Returns the uri if the node is a resource and the lexical form if the node is a literal
     */
    public static String nodeToString(RDFNode node) {
        if (node.isResource()) {
            return node.asResource().getURI();
        } else {
            return node.asLiteral().getLexicalForm();
        }
    }

    /**
     * Checks if a given node is a blank node named after our conventions starting with "■"
     * Anon nodes are not seen as blank nodes by this method
     *
     * @param node The node to be tested
     */
    public static boolean isBlankNode(RDFNode node) {
        return node.isResource() && node.asResource().getURI().startsWith(BNODE_PREFIX);
    }

    /**
     * Checks if a given string is a blank node uri named after our conventions starting with "■"
     *
     * @param string The string to be tested
     */
    public static boolean isBlankNode(String string) {
        return string.startsWith(BNODE_PREFIX);
    }


    /**
     * Removes the blank node prefixes from the uri
     */
    public static String shorten(String s) {
        return s.replaceFirst(BNODE_PREFIX_A, "").replaceFirst(BNODE_PREFIX_B, "");
    }

    public static int signDiff(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        int minLen = Math.min(s1.length(), s2.length());
        int maxLen = Math.max(s1.length(), s2.length());
        int score = 0; //Tells how good they match
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                score += Character.MAX_VALUE;
            } else {
                score += Character.MAX_VALUE - Math.abs(s1.charAt(i) - s2.charAt(i));
                break;
            }
        }
        return -score + Character.MAX_VALUE * maxLen;
    }

    /**
     * Checks if a node is either an actual literal in the graph
     * OR
     * was a literal before, but we converted it to an uri resource starting with the special literal symbol
     */
    public static boolean isActualLiteral(RDFNode node) {
        if (node.isLiteral()) {
            return true;
        }
        if (node.isAnon()) {
            return false;
        }
        return node.asResource().getURI().startsWith(literalMappingSeparator);
    }

    public static Set<Resource> computeBlankNodes(Model model) {
        Set<Resource> bnodes = new HashSet<>();
        model.listStatements().forEach(statement -> {
            if (isBlankNode(statement.getSubject())) {
                bnodes.add(statement.getSubject());
            }
            if (isBlankNode(statement.getObject())) {
                bnodes.add(statement.getObject().asResource());
            }
        });
        return bnodes;
    }

    public static Set<String> computeNonBlankNodes(Model model) {
        Set<String> nonBnodes = new HashSet<>();
        model.listStatements().forEach(statement -> {
            if (!isBlankNode(statement.getSubject())) {
                nonBnodes.add(statement.getSubject().getURI());
            }
            if (!isBlankNode(statement.getObject())) {
                nonBnodes.add(nodeToString(statement.getObject()));
            }
        });
        return nonBnodes;
    }

    /**
     * Computes the BRadius for a model
     */
    public static int computeMaxRadiusToNonBlank(Model model) {
        Set<Resource> blankNodes = computeBlankNodes(model);
        return blankNodes.stream().parallel().mapToInt(blankNode -> {
            Set<RDFNode> toVisit = new HashSet<>();
            Set<RDFNode> toVisitNext = new HashSet<>();
            Set<RDFNode> visited = new HashSet<>();
            int radius = 1;
            toVisit.add(blankNode);
            while (!toVisit.isEmpty()) {
                for (RDFNode resource : toVisit) {
                    visited.add(resource);
                    Set<RDFNode> neighbors = computeNeighbours(model, resource);
                    if (neighbors.stream().anyMatch(rdfNode -> !isBlankNode(rdfNode))) {
                        return radius;
                    } else {
                        toVisitNext.addAll(neighbors);
                    }
                }
                toVisit = toVisitNext;
                toVisit.removeAll(visited);
                toVisitNext = new HashSet<>();
                radius++;
            }
            return radius;
        }).max().getAsInt();
    }

    /**
     * Gets the whole BComponent of a blank node
     * Basically traverses the graph starting from node to its neighbours and their neighbours until we hit a non blank node, then we stop traversing this path
     *
     * @return
     */
    public static Model getBComponentWithNeighbours(Model model, Resource startNode) {
        Set<Resource> alreadyVisited = new HashSet<>();
        List<Resource> toVisit = new ArrayList<>();
        toVisit.add(startNode);
        Model subModel = ModelFactory.createDefaultModel();

        while (!toVisit.isEmpty()) {
            Resource currentNode = toVisit.get(0);
            toVisit.remove(currentNode);
            alreadyVisited.add(currentNode);

            Set<Statement> outgoing = new HashSet<>(model.listStatements(currentNode, null, (RDFNode) null).toSet());
            Set<Statement> incoming = new HashSet<>(model.listStatements(null, null, currentNode).toSet());
            for (Statement statement : outgoing) {
                subModel.add(statement);
                if (isBlankNode(statement.getObject())) {
                    if (!alreadyVisited.contains(statement.getObject().asResource())) {
                        toVisit.add(statement.getObject().asResource());
                    }
                }
            }
            for (Statement statement : incoming) {
                subModel.add(statement);
                if (isBlankNode(statement.getSubject())) {
                    if (!alreadyVisited.contains(statement.getSubject())) {
                        toVisit.add(statement.getSubject());
                    }
                }
            }
        }
        return subModel;
    }

    /**
     * Computes a set of all bnodes in the bcomponent of the given node
     */
    public static Set<Resource> computeBNodesInBComponent(Model model, Resource startNode) {
        Set<Resource> alreadyVisited = new HashSet<>();
        List<Resource> toVisit = new ArrayList<>();
        toVisit.add(startNode);

        while (!toVisit.isEmpty()) {
            Resource currentNode = toVisit.get(0);
            toVisit.remove(currentNode);
            alreadyVisited.add(currentNode);

            Set<Statement> outgoing = new HashSet<>(model.listStatements(currentNode, null, (RDFNode) null).toSet());
            Set<Statement> incoming = new HashSet<>(model.listStatements(null, null, currentNode).toSet());
            for (Statement statement : outgoing) {
                if (isBlankNode(statement.getObject())) {
                    if (!alreadyVisited.contains(statement.getObject().asResource())) {
                        toVisit.add(statement.getObject().asResource());
                    }
                }
            }
            for (Statement statement : incoming) {
                if (isBlankNode(statement.getSubject())) {
                    if (!alreadyVisited.contains(statement.getSubject())) {
                        toVisit.add(statement.getSubject());
                    }
                }
            }
        }
        return alreadyVisited;
    }

    /**
     * Creates a copy of a model
     */
    public static Model copyModel(Model model) {
        Model result = ModelFactory.createDefaultModel();
        model.listStatements().forEach(statement -> {
            Resource sub = result.createResource(statement.getSubject().getURI());
            Property pred = result.createProperty(statement.getPredicate().getURI());
            Resource obj = null;
            if (statement.getObject().isResource()) {
                obj = result.createResource(statement.getObject().asResource().getURI());
                result.add(sub, pred, obj);
            } else {
                result.add(sub, pred, statement.getObject());
            }
        });
        return result;
    }

    /**
     * Removes all named resources starting the prefix string from the graph
     */
    public static void removeBlankNodesWithPrefix(Model model, String prefix) {
        for (Statement statement : model.listStatements().toSet()) {
            if (isBlankNode(statement.getSubject())) {
                if (statement.getSubject().getURI().startsWith(prefix)) {
                    model.remove(statement);
                }
            }
            if (isBlankNode(statement.getObject())) {
                if (statement.getObject().asResource().getURI().startsWith(prefix)) {
                    model.remove(statement);
                }
            }

        }
    }

    public static boolean hasBlankNodeAsSubject(Statement statement) {
        return isBlankNode(statement.getSubject());
    }

    public static boolean hasBlankNodeAsObject(Statement statement) {
        return isBlankNode(statement.getObject());
    }

    /**
     * Computes the resource objects of all blank nodes that are present in this model either as subject or object
     */
    public static Set<Resource> extractAllBNodes(Model model) {
        Set<Resource> blankNodes = new HashSet<>();
        extractAllResources(model).forEach(resource -> {
            if (resource.getURI().startsWith(BNODE_PREFIX)) {
                blankNodes.add(resource);
            }
        });
        return blankNodes;
    }

    /**
     * Counts bnodes being present in a list of statements
     */
    public static int countBNodes(List<Statement> statements) {
        Set<String> subs = statements.stream().filter(statement -> isBlankNode(statement.getSubject())).map(statement -> statement.getSubject().getURI()).collect(Collectors.toSet());
        Set<String> obs = statements.stream().filter(statement -> isBlankNode(statement.getObject())).map(statement -> statement.getObject().asResource().getURI()).collect(Collectors.toSet());
        subs.addAll(obs);
        return subs.size();
    }


    /**
     * Returns all resources from the model that are contained in at least one triple
     */
    public static Set<Resource> extractAllResources(Model model) {
        Set<Resource> resources = new HashSet<>();
        model.listStatements().forEach(statement -> {
            resources.add(statement.getSubject());
            if (statement.getObject().isResource()) {
                resources.add(statement.getObject().asResource());
            }
        });
        return resources;
    }

    /**
     * Returns all resources from the model that are in a radius around the start resource
     * Radius 0 -> Only the start resource
     * Radius 1 -> All neighbours
     */
    public static LinkedHashSet<Resource> extractAllResourcesInRadius(Model model, Resource start, int radius) {
        LinkedHashSet<Resource> resources = new LinkedHashSet<>();
        resources.add(start);
        for (int i = 0; i < radius; i++) {
            LinkedHashSet<Resource> toAdd = new LinkedHashSet<>();
            for (Resource resource : resources) {
                toAdd.addAll(computeNeighbours(model, resource).stream().map(RDFNode::asResource).collect(Collectors.toSet()));
            }
            resources.addAll(toAdd);
        }
        return resources;
    }


    /**
     * Returns all rdfNodes from the model that are contained in at least one triple
     */
    public static Set<RDFNode> extractAllRDFNodes(Model model) {
        Set<RDFNode> rdfNodes = new HashSet<>();
        model.listStatements().forEach(statement -> {
            rdfNodes.add(statement.getSubject());
            rdfNodes.add(statement.getObject());
        });
        return rdfNodes;
    }

    /**
     * Returns all predicates from the model that are contained in at least one triple
     */
    public static Set<Property> extractAllPredicates(Model model) {
        Set<Property> propertySet = new HashSet<>();
        model.listStatements().forEach(statement -> {
            propertySet.add(statement.getPredicate());
        });
        return propertySet;
    }

    /**
     * Replaces multiple resource in a graph (all triples) with another resource
     *
     * @param replacements Key value pairs of the thing that should be replaced and the thing that it should be replaced with
     */
    public static void replaceResource(Model model, Map<RDFNode, Resource> replacements) {
        Set<Statement> updatedStatements = new HashSet<>();
        for (Statement statement : model.listStatements().toSet()) {
            Resource sub = null;
            Property p = statement.getPredicate();
            RDFNode obj = null;


            if (replacements.containsKey(statement.getSubject())) {
                sub = replacements.get(statement.getSubject());
            } else {
                sub = statement.getSubject();
            }
            if (replacements.containsKey(statement.getObject())) {
                obj = replacements.get(statement.getObject());
            } else {
                obj = statement.getObject();
            }
            model.remove(statement);

            updatedStatements.add(model.createStatement(sub, p, obj));
        }

        for (Statement updatedStatement : updatedStatements) {
            model.add(updatedStatement);
        }
    }

    /**
     * Returns a set with
     */
    public static Collection<Set<Resource>> getAllBComponentsBNodes(Model model) {
        Set<Resource> blankNodes = computeBlankNodes(model);
        HashMap<String, Set<Resource>> result = new HashMap<>();

        for (Resource blankNode : blankNodes) {
            Set<Resource> resources = computeBNodesInBComponent(model, blankNode);
            result.put(resources.stream().map(Resource::getURI).sorted().findFirst().get(), resources);
        }

        return result.values();
    }

}
