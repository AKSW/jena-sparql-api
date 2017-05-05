package org.aksw.jena_sparql_api.jgrapht;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.JFrame;

import org.aksw.commons.collections.MapUtils;
import org.aksw.commons.collections.multimaps.BiHashMultimap;
import org.aksw.commons.collections.trees.LabeledNodeImpl;
import org.aksw.commons.collections.trees.LabeledTree;
import org.aksw.commons.collections.trees.ReclaimingSupplier;
import org.aksw.jena_sparql_api.concept_cache.op.OpExtConjunctiveQuery;
import org.aksw.jena_sparql_api.jgrapht.transform.GraphIsoMap;
import org.aksw.jena_sparql_api.jgrapht.transform.GraphIsoMapImpl;
import org.aksw.jena_sparql_api.jgrapht.transform.GraphVar;
import org.aksw.jena_sparql_api.jgrapht.transform.QueryToGraphVisitor;
import org.aksw.jena_sparql_api.jgrapht.transform.QueryToJenaGraph;
import org.aksw.jena_sparql_api.jgrapht.wrapper.PseudoGraphJenaGraph;
import org.aksw.jena_sparql_api.views.index.SparqlViewMatcherOpImpl;
import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.compose.Difference;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpExt;
import org.jgraph.JGraph;
import org.jgrapht.ext.JGraphModelAdapter;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.jgraph.layout.JGraphFacade;
import com.jgraph.layout.JGraphLayout;
import com.jgraph.layout.hierarchical.JGraphHierarchicalLayout;

import jersey.repackaged.com.google.common.collect.Sets;


class ExtendedQueryToGraphVisitor
    extends QueryToGraphVisitor
{
    public ExtendedQueryToGraphVisitor() {
        super();
    }

//    public ExtendedQueryToGraphVisitor(Graph graph, Supplier<Node> nodeSupplier) {
//        super(graph, nodeSupplier);
//    }

    public ExtendedQueryToGraphVisitor(Supplier<Node> nodeSupplier) {
        super(nodeSupplier);
    }

    @Override
    public void visit(OpExt op) {
        if(op instanceof OpExtConjunctiveQuery) {
            ((OpExtConjunctiveQuery) op).getQfpc().toOp().visit(this);
        }
    }

}


class InsertPosition<K> {
    protected GraphIndexNode<K> node;
    protected GraphIsoMap graphIso;
    public InsertPosition(GraphIndexNode<K> node, GraphIsoMap graphIso) {
        super();
        this.node = node;
        this.graphIso = graphIso;
    }
    public GraphIndexNode<K> getNode() {
        return node;
    }
    public GraphIsoMap getGraphIso() {
        return graphIso;
    }



}

/**
 *
 *
 * FIXME Materialize if nesting of views gets too deep
 * FIXME Cluster graphs by size (or maybe this is separate indices?) - we don't want to compute isomorphisms from { ?s ?p ?o } to a BGP with 100 TPs
 *
 * Materialization vs recomputation of iso-mappings
 *
 * We check whether a graph being inserted is subsumed by other graphs in regard to isomorphism.
 * Insert graph = graph being inserted
 * Node graph = graph at the current index node
 * As there can be several mappings between in insert graph and the node graph, we can always re-compute the set of iso mappings
 * based on the path to the root.
 *
 *
 *
 *
 * @author raven
 *
 */
class GraphIndexNode<K>
    extends LabeledNodeImpl<Long, GraphIndexNode<K>, GraphIndex<K>>
{
    public GraphIndexNode(GraphIndex<K> tree, Long id) {
        super(tree, id);
    }

    public GraphIsoMap getValue() {
        return tree.idToGraph.get(id);
    }

    public GraphIsoMap setValue(GraphIsoMap graphIso) {
        return tree.idToGraph.put(id, graphIso);
    }

    Set<K> getKeys() {
        return tree.idToKeys.get(id);
    }
}

class GraphIndex<K>
    extends LabeledTree<Long, GraphIndexNode<K>>
{
    public static GraphIndex<Node> create() {
        int i[] = {0};
        Supplier<Node> idSupplier = () -> NodeFactory.createURI("http://index.node/id" + i[0]++);
        GraphIndex<Node> result = new GraphIndex<>(idSupplier);
        return result;
    }

    public GraphIndex(Supplier<K> keySupplier) {
        super();
        this.keySupplier = keySupplier;

        long i[] = {0};
        idSupplier = new ReclaimingSupplier<>(() -> i[0]++);


        rootNode = createNode(null);
        root = rootNode.getKey();

    }

    Map<Long, GraphIsoMap> idToGraph = new HashMap<>();


//    long nextId[] = {0};
//    protected ReclaimingSupplier<Long> idSupplier = new ReclaimingSupplier<>(() -> nextId[0]++ );


//    protected GraphIndexNode<K> root = new GraphIndexNode<K>(new GraphIsoMapImpl(new GraphVarImpl(), HashBiMap.create()));
//
    protected Supplier<K> keySupplier;
//    protected Map<Long, GraphIndexNode<K>> IdToNode = new HashMap<>();
//    protected ReversibleMap<Long, Long> childToParent = new ReversibleMapImpl<>();

    protected BiHashMultimap<Long, K> idToKeys = new BiHashMultimap<>();



    public void removeNodesByKey(K key) {

    }

    public void removeNodeById(Long id) {

    }


    /**
     * Create a detached node
     *
     * @param graphIso
     * @return
     */
    protected GraphIndexNode<K> createNode(GraphIsoMap graphIso) {
        Long id = idSupplier.get();
        GraphIndexNode<K> result = new GraphIndexNode<K>(this, id);
        keyToNode.put(id, result);

        result.setValue(graphIso);

        return result;
    }



    /**
     * Insert the graph and allocate a fresh, unused, id
     *
     * @param graph
     */
    public K add(Graph graph) {
        K key = keySupplier.get();

        put(key, graph);

        return key;
    }

    public Multimap<K, Map<Node, Node>> lookup(Graph graph) {

        Collection<InsertPosition<K>> positions = new LinkedList<>();
        findInsertPositions(positions, rootNode, graph, HashBiMap.create(), IndentedWriter.stderr);

        Multimap<K, Map<Node, Node>> result = HashMultimap.create();
        for(InsertPosition<K> pos : positions) {
            for(K key : pos.node.getKeys()) {
                result.put(key, pos.graphIso.getInToOut());
            }
        }
        return result;
    }

    /**
     * Insert a graph pattern with a specific key, thereby replacing any existing one having this key already
     *
     * @param key
     * @param graph
     * @return
     */
    K put(K key, Graph graph) {
        add(key, graph, HashBiMap.create(), IndentedWriter.stderr);

        return null;
    }


    /**
     * Clones a sub tree thereby removing the triples in the removal graph
     *
     *
     * @param removalGraph
     * @param writer
     * @return
     */
    GraphIndexNode<K> cloneWithRemoval(GraphIndexNode<K> node, Graph removalGraph, IndentedWriter writer) {
        GraphIsoMap graphIso = node.getValue();
        BiMap<Node, Node> nodeIso = graphIso.getInToOut();
        GraphIsoMap mappedRemovalGraph = new GraphIsoMapImpl(removalGraph, nodeIso);

        //System.out.println("REMOVAL");
        //RDFDataMgr.write(System.out, mappedRemovalGraph, RDFFormat.NTRIPLES);
        //System.out.println("DATA");
        //RDFDataMgr.write(System.out, graphIso, RDFFormat.NTRIPLES);

        // Create a clone of the node graph
        // Alternatively, create a Difference view
        //Graph cloneGraph = GraphFactory.createDefaultGraph();
        //
        Graph cloneGraph = new Difference(graphIso.getWrapped(), mappedRemovalGraph);
        writer.println("Cloned graph size reduced from  " + graphIso.size() + " -> " + cloneGraph.size());

        GraphIndexNode<K> result = createNode(new GraphIsoMapImpl(cloneGraph, nodeIso));

        // Then for each child: map the removal graph according to the child's iso
        for(GraphIndexNode<K> child : node.getChildren()) {
            GraphIndexNode<K> cloneChild = cloneWithRemoval(child, mappedRemovalGraph, writer);
            result.appendChild(cloneChild);
        }

        return result;
    }



    void findInsertPositions(Collection<InsertPosition<K>> out, GraphIndexNode<K> node, Graph insertGraph, BiMap<Node, Node> baseIso, IndentedWriter writer) {
        //children.forEach(child -> {


        boolean isSubsumed[] = {false};

        writer.incIndent();
        for(GraphIndexNode<K> child : node.getChildren()) {
        //children.stream().flatMap(child -> {
            //BiMap<Node, Node> isoMap = child.graphIso.getOutToIn();
            //Graph candGraph = new GraphIsoMapImpl(graph, isoMap);
            GraphIsoMap viewGraph = child.getValue();

            writer.println("Comparison with view graph of size " + viewGraph.size());
//            RDFDataMgr.write(System.out, viewGraph, RDFFormat.NTRIPLES);
//            System.out.println("under: " + currentIso);

            // For every found isomorphism, check all children whether they are also isomorphic.
            //
            writer.incIndent();
            int i[] = {0};
            QueryToJenaGraph.match(baseIso, viewGraph, insertGraph).forEach(iso -> {
                isSubsumed[0] = true;

                writer.println("Found match #" + ++i[0] + ":");
                writer.incIndent();

                // Do this state space search thingy: update the state, track the changes, compute and restore
                // This means: track which keys will be added, add them, and later remove them again
                boolean isCompatible = MapUtils.isCompatible(iso, baseIso);
                if(!isCompatible) {
                    writer.println("Not compatible with current mapping");
                    writer.incIndent();
                    writer.println("baseIso: " + baseIso);
                    writer.println("iso: " + iso);
                    writer.decIndent();
                    throw new RuntimeException("This should never happen - unless either there is a bug or even worse there is a conecptual issues");
                    //return;
                }
                Set<Node> affectedKeys = new HashSet<>(Sets.difference(iso.keySet(), baseIso.keySet()));
                affectedKeys.forEach(k -> baseIso.put(k, iso.get(k)));
                baseIso.putAll(iso);
                writer.println("Contributed " + affectedKeys + " yielding iso mapping: " + iso);

                // iso: how to rename nodes of the view graph so it matches with the insert graph
                Graph g = new GraphIsoMapImpl(viewGraph, iso);

                Difference diff = new Difference(g, viewGraph); // left side should be the smaller graph for performance

                // now create the diff between the insert graph and mapped child graph
                writer.println("Diff has " + diff.size() + " triples at depth " + writer.getUnitIndent());
                //RDFDataMgr.write(System.out, diff, RDFFormat.NTRIPLES);



                // if the diff is empty, associate the pattern id with this node

                // on non empty diff, add the subgraphs as children to that node
                if(diff.isEmpty()) {

                } else {
                    ///child.add(diff, iso, writer);
                    // yield this as an insert position
                    //InsertPosition<K> p = new InsertPosition(this, viewGraph);
                    findInsertPositions(out, child, diff, iso, writer);
                }

                affectedKeys.forEach(baseIso::remove);

                writer.decIndent();
            });
            writer.decIndent();
        }
        writer.decIndent();

        if(!isSubsumed[0]) {
            // Make a copy of the baseIso, as it is transient due to state space search
            GraphIsoMap gim = new GraphIsoMapImpl(insertGraph, HashBiMap.create(baseIso));
            InsertPosition<K> pos = new InsertPosition<>(node, gim);
            out.add(pos);
        }
    }

    /**
     * During the insert procedure, the insert graph is never renamed, because we want to figure out
     * how to remap existing nodes such they become a subgraph of the insertGraph.
     *
     * @param graph
     */
    void add(K key, Graph insertGraph, BiMap<Node, Node> baseIso, IndentedWriter writer) {
        // The insert graph must be larger than the node Graph

        Collection<InsertPosition<K>> positions = new LinkedList<>();
        findInsertPositions(positions, rootNode, insertGraph, baseIso, writer);

        for(InsertPosition<K> pos : positions) {
            performAdd(key, pos, writer);
        }
    }

    void performAdd(K key, InsertPosition<K> pos, IndentedWriter writer) {
        GraphIndexNode<K> node = pos.getNode();
        GraphIsoMap graphIso = pos.getGraphIso();
        Graph insertGraph = graphIso.getWrapped();
        BiMap<Node, Node> baseIso = graphIso.getInToOut();

        writer.println("Insert attempt of user graph of size " + insertGraph.size());
//        RDFDataMgr.write(System.out, insertGraph, RDFFormat.NTRIPLES);
//        System.out.println("under: " + currentIso);

        // If the addition is not on a leaf node, check if we subsume anything
        boolean isSubsumed[] = {!node.getChildren().isEmpty()}; //{false};


        // Make a copy of the baseIso, as it is transient due to state space search
        //GraphIsoMap gim = new GraphIsoMapImpl(insertGraph, HashBiMap.create(baseIso));

        boolean wasAdded[] = {false};

        // If the insertGraph was not subsumed,
        // check if it subsumes any of the other children
        // for example { ?s ?p ?o } may not be subsumed by an existing child, but it will subsume any other children
        // use clusters
        // add it as a new child
        if(!isSubsumed[0]) {
            writer.println("We are not subsumed, but maybe we subsume");
            GraphIndexNode<K> subsumeRoot = createNode(graphIso);//new GraphIndexNode<K>(graphIso);


            //for(GraphIndexNode child : children) {
            Iterator<GraphIndexNode<K>> it = node.getChildren().iterator();//children.listIterator();
            while(it.hasNext()) {
                GraphIndexNode<K> child = it.next();
                GraphIsoMap viewGraph = child.getValue();

                writer.println("Comparison with view graph of size " + viewGraph.size());
//                RDFDataMgr.write(System.out, viewGraph, RDFFormat.NTRIPLES);
//                System.out.println("under: " + currentIso);

                // For every found isomorphism, check all children whether they are also isomorphic.
                writer.incIndent();
                int i[] = {0};
                QueryToJenaGraph.match(baseIso.inverse(), insertGraph, viewGraph).forEach(iso -> {
                    writer.println("Detected subsumption #" + ++i[0]);
                    writer.incIndent();

                    GraphIsoMap insertGraphX = new GraphIsoMapImpl(insertGraph, iso);
//                    System.out.println("Remapped insert via " + iso);
//                    RDFDataMgr.write(System.out, insertGraphX, RDFFormat.NTRIPLES);
//                    System.out.println("---");

                    Difference diff = new Difference(viewGraph, insertGraphX);


                    GraphIndexNode<K> newChild = cloneWithRemoval(child, diff, writer);

                    subsumeRoot.appendChild(newChild);//add(newChild, baseIso, writer);
                    writer.decIndent();
                });

                if(i[0] > 0) {
                    // Remove all mappings of keys for that node
                    //child.itemIds


                    writer.println("A node was subsumed and therefore removed");
                    wasAdded[0] = true;
                    // not sure if this remove works
                    it.remove();
                }
                writer.decIndent();

            }


        }

        // If nothing was subsumed, add it to this node
        if(!wasAdded[0]) {
            node.appendChild(createNode(graphIso));
        }
    }

}



public class MainSparqlQueryToGraph {



    public static void main(String[] args) {
//        org.apache.jena.graph.Graph g = GraphFactory.createDefaultGraph();
//        g.add(new Triple(Vars.s, Vars.p, Vars.o));
//        RDFDataMgr.write(System.out, g, RDFFormat.NTRIPLES);
        String[][] cases = {
            { "Prefix : <http://ex.org/> Select * { ?a ?b ?c }",
              "Prefix : <http://ex.org/> Select * { ?x ?y ?z }", },
            { "Prefix : <http://ex.org/> Select * { ?d a ?f ; ?g ?h }",
              "Prefix : <http://ex.org/> Select * { ?x a ?o ; ?y ?z }" },
            { "Prefix : <http://ex.org/> Select * { ?i a :Bakery ; :locatedIn :Leipzig }",
              "Prefix : <http://ex.org/> Select * { ?x a ?o . ?x ?y ?z }" },
            { "Prefix : <http://ex.org/> Select * { ?x a ?o . ?x ?y ?z . ?z a ?w}" }
        };


        String caseA = cases[0][0];
        String caseB = cases[1][0];
        String caseC = cases[3][0];

        Op aop = Algebra.toQuadForm(Algebra.compile(QueryFactory.create(caseA)));
        Op bop = Algebra.toQuadForm(Algebra.compile(QueryFactory.create(caseB)));
        Op cop = Algebra.toQuadForm(Algebra.compile(QueryFactory.create(caseC)));
        //System.out.println(op);


        aop = SparqlViewMatcherOpImpl.normalizeOp(aop);
        bop = SparqlViewMatcherOpImpl.normalizeOp(bop);
        cop = SparqlViewMatcherOpImpl.normalizeOp(cop);


        //RDFDataMgr.write(System.out, graph, RDFFormat.NTRIPLES);

        Supplier<Supplier<Node>> ssn = () -> { int[] x = {0}; return () -> NodeFactory.createBlankNode("_" + x[0]++); };
        QueryToGraphVisitor av = new ExtendedQueryToGraphVisitor(ssn.get());
        aop.visit(av);
        GraphVar ag = av.getGraph();
        //System.out.println(ag.get);

        QueryToGraphVisitor bv = new ExtendedQueryToGraphVisitor(ssn.get());
        bop.visit(bv);
        GraphVar bg = bv.getGraph();

        QueryToGraphVisitor cv = new ExtendedQueryToGraphVisitor(ssn.get());
        cop.visit(cv);
        GraphVar cg = bv.getGraph();


//        System.out.println("Graph A:");
//        RDFDataMgr.write(System.out, ag.getWrapped(), RDFFormat.NTRIPLES);
//        System.out.println(ag.getVarToNode());
//
//        System.out.println("Graph B:");
//        RDFDataMgr.write(System.out, bg.getWrapped(), RDFFormat.NTRIPLES);
//
        List<Map<Node, Node>> solutions = QueryToJenaGraph.match(Collections.emptyMap(), bg, ag).collect(Collectors.toList());

        System.out.println("VarMap entries: " + solutions.size());
        solutions.forEach(varMap -> System.out.print(varMap));


        GraphIndex<Node> index = GraphIndex.create();
        int xxx = 1;

        if(xxx == 0) {
            // incremental subsumtion
            index.add(ag);
            index.add(bg);
            index.add(cg);
        } else {
            // most generic inserted last
            index.add(bg);
            index.add(cg);
            index.add(ag);
        }

        index.lookup(cg).entries().forEach(e -> System.out.println("Lookup item: " + e));


        //SparqlQueryContainmentUtils.match(viewQuery, userQuery, qfpcMatcher)
        org.jgrapht.Graph<?, ?> dg = new PseudoGraphJenaGraph(ag);
        //System.out.println(graph);
        if(false) {
            visualizeGraph(dg);
        }
    }


    public static void visualizeGraph(org.jgrapht.Graph<?, ?> graph) {
        JFrame frame = new JFrame();
        frame.setSize(1000, 800);
        JGraph jgraph = new JGraph(new JGraphModelAdapter(graph));
        jgraph.setScale(2);
        final JGraphLayout hir = new JGraphHierarchicalLayout();
        // final JGraphLayout hir = new JGraphSelfOrganizingOrganicLayout();

        final JGraphFacade graphFacade = new JGraphFacade(jgraph);
        hir.run(graphFacade);
        final Map nestedMap = graphFacade.createNestedMap(true, true);
        jgraph.getGraphLayoutCache().edit(nestedMap);

        frame.getContentPane().add(jgraph);
        frame.setVisible(true);
    }

}
