package org;

import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aksw.jena_sparql_api.concept_cache.collection.FeatureMap;
import org.aksw.jena_sparql_api.concept_cache.collection.FeatureMapImpl;
import org.aksw.jena_sparql_api.concept_cache.domain.QuadFilterPatternCanonical;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.algebra.Op;

/**
 *
 * @author raven
 *
 * @param <C> (Cache) Item object type
 * @param <Q> Query object type
 * @param <D> Type of the (D)ata associated with cache objects
 * @param <F> (F)eature type
 */
public class IndexSystem<C, Q, D, F> {

    /**
     * Function that for a given item yields a stream (set) of
     * feature sets describing the item.
     * Feature sets can be considered disjunctive, i.e. a lookup will yield the item,
     * if any of its describing feature sets match.
     *
     */
    protected Function<C, Stream<Set<F>>> itemFeatureExtractor;

    /**
     * Function for extracting features from a query object
     *
     */
    protected Function<Q, Stream<Set<F>>> queryFeatureExtractor;

    /**
     * The item store
     *
     */
    protected FeatureMap<F, Entry<C, D>> featuresToItems;




    public IndexSystem(
            Function<C, Stream<Set<F>>> itemFeatureExtractor,
            Function<Q, Stream<Set<F>>> queryFeatureExtractor)
    //        FeatureMap<F, Entry<C, D>> featuresToItems)
    {
        super();
        this.itemFeatureExtractor = itemFeatureExtractor;
        this.queryFeatureExtractor = queryFeatureExtractor;
        this.featuresToItems = new FeatureMapImpl<F, Entry<C, D>>();
//        this.featuresToItems = featuresToItems;
    }

    public void put(C item, D data) {
        itemFeatureExtractor
            .apply(item)
            .forEach(featureSet -> {
                featuresToItems.put(featureSet, new SimpleEntry<>(item, data));
            });
    }

    public Set<Entry<C, D>> lookup(Q query) {
        Set<Entry<C, D>> candidateEntries = queryFeatureExtractor
            .apply(query)
            .flatMap(featureSet -> featuresToItems.getIfSubsetOf(featureSet).stream())
            .map(e -> e.getValue())
            .collect(Collectors.toSet());

        return candidateEntries;
    }


    public static IndexSystem<String, QuadFilterPatternCanonical, Query> create() {
        Function<Op, Stream<Set<String>>> featureExtractor = (oop) ->
            Collections.singleton(OpVisitorFeatureExtractor.getFeatures(oop, (op) -> op.getClass().getSimpleName())).stream();

        FeatureMap<String, Op> featureMap = new FeatureMapImpl<>();

        IndexSystem<Op, Op, String> result = new IndexSystem<>(
            featureExtractor,
            featureExtractor,
            featureMap);

        return result;

    }

}
