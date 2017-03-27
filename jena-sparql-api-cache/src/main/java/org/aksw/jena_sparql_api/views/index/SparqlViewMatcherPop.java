package org.aksw.jena_sparql_api.views.index;

import java.util.Map;

import org.aksw.jena_sparql_api.concept_cache.core.ProjectedOp;
import org.aksw.jena_sparql_api.view_matcher.OpVarMap;

public interface SparqlViewMatcherPop<K> {
    //boolean acceptsAdd(Op op);

	ProjectedOp getPop(K key);

    void put(K key, ProjectedOp op);

    //KeyedOpVarMap<K> lookupSingle(Op op);
    //Collection<KeyedOpVarMap<K>> lookup(Op op);
    /**
     * The result should be a LinkedHashMap of candidate matches - i.e. the entry set should be ordered, with the 'best' match first
     *
     * @param op
     * @return
     */
    Map<K, OpVarMap> lookup(ProjectedOp op);

    void removeKey(Object key);
    //void remove(V key);
}
