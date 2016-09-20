package org.aksw.jena_sparql_api.views.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.aksw.commons.collections.trees.Tree;
import org.aksw.commons.util.strings.StringUtils;
import org.aksw.jena_sparql_api.algebra.transform.TransformJoinToConjunction;
import org.aksw.jena_sparql_api.algebra.transform.TransformUnionToDisjunction;
import org.aksw.jena_sparql_api.concept_cache.collection.FeatureMap;
import org.aksw.jena_sparql_api.concept_cache.collection.FeatureMapImpl;
import org.aksw.jena_sparql_api.concept_cache.core.SparqlCacheUtils;
import org.aksw.jena_sparql_api.concept_cache.op.OpUtils;
import org.aksw.jena_sparql_api.unsorted.OpVisitorFeatureExtractor;
import org.aksw.jena_sparql_api.utils.Generator;
import org.aksw.jena_sparql_api.utils.VarGeneratorImpl2;
import org.aksw.jena_sparql_api.view_matcher.OpVarMap;
import org.aksw.jena_sparql_api.view_matcher.SparqlViewMatcherUtils;
import org.apache.jena.ext.com.google.common.collect.Iterables;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.Transformer;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.core.Var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Multimap;


public class OpViewMatcherImpl
	implements OpViewMatcher
{

    private static final Logger logger = LoggerFactory
            .getLogger(OpViewMatcherImpl.class);

    protected Function<Op, Op> opNormalizer;
    protected Function<Op, Set<Set<String>>> itemFeatureExtractor;
    protected Function<Op, QueryIndex> itemIndexer;
    protected FeatureMap<String, MyEntry> featuresToIndexes;
    protected Map<Node, MyEntry> idToQueryIndex;

	public OpViewMatcherImpl(
	        Function<Op, Op> opNormalizer,
            Function<Op, Set<Set<String>>> itemFeatureExtractor,
            Function<Op, QueryIndex> itemIndexer) {
        super();
        this.opNormalizer = opNormalizer;
        this.itemFeatureExtractor = itemFeatureExtractor;
        this.itemIndexer = itemIndexer;
        this.featuresToIndexes = new FeatureMapImpl<>(); //featuresToIndexes;

        idToQueryIndex = new HashMap<>();
    }

    @Override
	public Node add(Op item) {
        Op normalizedItem = opNormalizer.apply(item);

    	Node id = NodeFactory.createURI("id://" + StringUtils.md5Hash("" + normalizedItem));
        QueryIndex index = itemIndexer.apply(normalizedItem);

        Set<Set<String>> featureSets = itemFeatureExtractor.apply(normalizedItem);
        MyEntry entry = new MyEntry(id, featureSets, index);

        for(Set<String> featureSet : featureSets) {
            featuresToIndexes.put(featureSet, entry); // new SimpleEntry<>(item, data)
        }

        idToQueryIndex.put(id, entry);

        return id;
	}


    /**
     * Lookup a single candidate
     */
    public LookupResult lookupSingle(Op item) {
    	Collection<LookupResult> tmp = lookup(item);
    	LookupResult result = Iterables.getFirst(tmp, null);
    	return result;
    }


	@Override
	public Collection<LookupResult> lookup(Op item) {
	    Op normalizedItem = opNormalizer.apply(item);
	    Set<MyEntry> tmpCands = new HashSet<>();

        itemFeatureExtractor.apply(normalizedItem).forEach(featureSet -> {
            //featuresToIndexes.getIfSubsetOf(featureSet).stream()
            featuresToIndexes.get(featureSet).stream()
                //.map(e -> e.getValue())
                .forEach(x -> tmpCands.add(x));
        });

        // Order candidates by their node count - largest node counts first
        List<MyEntry> cands = new ArrayList<>(tmpCands);
        Collections.sort(cands, (a, b) -> ((int)(a.queryIndex.getTree().nodeCount() - b.queryIndex.getTree().nodeCount())));

        if(logger.isDebugEnabled()) { logger.debug("Phase 1: " + cands.size() + "/" + featuresToIndexes.size() + " passed"); }
        QueryIndex queryIndex = itemIndexer.apply(normalizedItem);


        List<LookupResult> result = new ArrayList<>();

        for(MyEntry cacheEntry : cands) {
        	QueryIndex cacheIndex = cacheEntry.queryIndex;

            Multimap<Op, Op> candOpMapping = SparqlViewMatcherSystemImpl.getCandidateLeafMapping(cacheIndex, queryIndex);
            Tree<Op> cacheTree = cacheIndex.getTree();
            Tree<Op> queryTree = queryIndex.getTree();

            // TODO: Require a complete match of the tree - i.e. cache and query trees must have same number of nodes / same depth / some other criteria that can be checked quickly
            // In fact, we could use these features as an additional index
            Stream<OpVarMap> opVarMaps = SparqlViewMatcherUtils.generateTreeVarMapping(candOpMapping, cacheTree, queryTree);

            opVarMaps.forEach(opVarMap -> {
                Op cacheRoot = cacheTree.getRoot();
                Op queryRoot = opVarMap.getOpMap().get(cacheRoot);
                if(logger.isDebugEnabled()) { logger.debug("query root: " + queryRoot); }


                Node id = cacheEntry.id;
                // We need to update the queryIndex (remove sub-trees that matched)
                Tree<Op> r = applyMapping(id, cacheTree, queryTree, opVarMap);

                if(logger.isDebugEnabled()) { logger.debug("Result: " + r); }
                if(logger.isDebugEnabled()) { logger.debug("Varmap: " + Iterables.toString(opVarMap.getVarMaps())); }

                LookupResult lr = new LookupResult(cacheEntry, opVarMap);
                result.add(lr);
                //return lr;
            });

        }

		return result;
	}

	public static Tree<Op> applyMapping(Node id, Tree<Op> cacheTree, Tree<Op> queryTree, OpVarMap opVarMap) {
	    Map<Op, Op> nodeMapping = opVarMap.getOpMap();

	    Op sourceRoot = cacheTree.getRoot();
	    Op targetNode = nodeMapping.get(sourceRoot);

	    if(targetNode == null) {
	        throw new RuntimeException("Could not match root node of a source tree to a node in the target tree - Should not happen.");
	    }

	    //QuadPattern yay = new QuadPattern();
	    //Node serviceNode = NodeFactory.createURI("");
	    OpService placeholderOp = new OpService(id, new OpBGP(), true);
	    Op repl = OpUtils.substitute(queryTree.getRoot(), false, op -> {
	       return op == targetNode ? placeholderOp : null;
	    });

	    Tree<Op> result = OpUtils.createTree(repl);
	    return result;
	}




	public static Op normalizeOp(Op op) {
        op = Transformer.transform(TransformJoinToConjunction.fn, Transformer.transform(TransformUnionToDisjunction.fn, op));

        Generator<Var> generatorCache = VarGeneratorImpl2.create();
        Op result = OpUtils.substitute(op, false, (o) -> SparqlCacheUtils.tryCreateCqfp(o, generatorCache));

        return result;

	}


	public static OpViewMatcher create() {
        Function<Op, Set<Set<String>>> itemFeatureExtractor = (oop) ->
        	Collections.singleton(OpVisitorFeatureExtractor.getFeatures(oop, (op) -> op.getClass().getSimpleName()));

        OpViewMatcher result = new OpViewMatcherImpl(
        		OpViewMatcherImpl::normalizeOp,
                itemFeatureExtractor,
                new QueryIndexerImpl());

        return result;
	}
}
