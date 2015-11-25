package org.aksw.jena_sparql_api.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.sparql.core.BasicPattern;
import com.hp.hpl.jena.sparql.core.Quad;
import com.hp.hpl.jena.sparql.core.QuadPattern;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.graph.GraphFactory;
import com.hp.hpl.jena.sparql.util.NodeComparator;
import com.hp.hpl.jena.sparql.util.TripleComparator;

public class QuadPatternUtils {


    public static String toNTripleString(QuadPattern quadPattern) throws Exception {

        List<Quad> quads = quadPattern.getList();

        String result = "";

        for(Quad quad : quads) {
            Triple triple = quad.asTriple();
            String tmp = TripleUtils.toNTripleString(triple);

            if(!result.isEmpty()) {
                result += "\n";
            }

            result += tmp;
        }

        return result;
    }

    public static QuadPattern create(Iterable<Quad> quads) {
        QuadPattern result = new QuadPattern();
        for(Quad quad : quads) {
            result.add(quad);
        }



        return result;
    }

    //public static QuadPattern

    public static QuadPattern toQuadPattern(BasicPattern basicPattern) {
        return toQuadPattern(Quad.defaultGraphNodeGenerated, basicPattern);
    }

    // This method is implictely part of OpQuadPattern, but its not reusable yet
    public static QuadPattern toQuadPattern(Node g, BasicPattern basicPattern) {

        QuadPattern result = new QuadPattern();
        for(Triple triple : basicPattern) {
            Quad quad = new Quad(g, triple);
            result.add(quad);
        }

        return result;
    }

    /**
     * Creates a set of triples by omitting the graph node of the quads
     *
     * @param quadPattern
     * @return
     */
    public static BasicPattern toBasicPattern(QuadPattern quadPattern)
    {
        BasicPattern result = new BasicPattern();

        for(Quad quad : quadPattern) {
            Triple triple = quad.asTriple();
            result.add(triple);
        }

        return result;
    }

    public static Map<Node, BasicPattern> indexBasicPattern(Iterable<Quad> quads)
    {
        Map<Node, BasicPattern> result = new HashMap<Node, BasicPattern>(); //new TreeMap<Node, BasicPattern>(new NodeComparator());

        for(Quad q : quads) {
            BasicPattern basicPattern = result.get(q.getGraph());
            if(basicPattern == null) {
                basicPattern = new BasicPattern();
                result.put(q.getGraph(), basicPattern);
            }

            basicPattern.add(q.asTriple());
        }

        return result;
    }


    public static Map<Node, Set<Triple>> indexSorted(Iterable<Quad> quads)
    {
        Map<Node, Set<Triple>> result = new TreeMap<Node, Set<Triple>>(new NodeComparator());
        for(Quad q : quads) {
            Set<Triple> triples = result.get(q.getGraph());
            if(triples == null) {
                triples = new TreeSet<Triple>(new TripleComparator());
                result.put(q.getGraph(), triples);
            }

            triples.add(q.asTriple());
        }

        return result;
    }

    public static Map<Node, Graph> indexAsGraphs(Iterable<Quad> quads) {
        Map<Node, Graph> result = indexAsGraphs(quads.iterator());
        return result;
    }

    public static Map<Node, Graph> indexAsGraphs(Iterator<Quad> it) {
        Map<Node, Graph> result = new HashMap<Node, Graph>();
        while(it.hasNext()) {
            Quad quad = it.next();

            Graph graph = result.get(quad.getGraph());
            if(graph == null) {
                graph = GraphFactory.createDefaultGraph();
                result.put(quad.getGraph(), graph);
            }

            graph.add(quad.asTriple());
        }

        return result;
    }

    public static Set<Var> getVarsMentioned(Iterable<Quad> quadPattern) {
        Set<Var> result = new HashSet<Var>();
        for (Quad quad : quadPattern) {
            Set<Var> tmp = QuadUtils.getVarsMentioned(quad);
            result.addAll(tmp);
        }
    
        return result;
    }

}
