package org.aksw.jena_sparql_api.concept_cache.core;

import java.util.HashSet;
import java.util.Set;

import org.aksw.jena_sparql_api.concept_cache.dirty.SparqlViewCache;
import org.aksw.jena_sparql_api.concept_cache.domain.ProjectedQuadFilterPattern;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.Var;

/**
 * This class' purpose is to execute queries and index the result set
 * by associating it with a given algebra expression.
 *
 * Because of the need of the algebra expression, this class is not a QueryExecutionFactory directly, but it aggregates it.
 *
 * This class is not intended to be used directly. Instead,
 *
 * @author raven
 *
 */
public class ViewCacheIndexerImpl
    implements ViewCacheIndexer
{
    protected QueryExecutionFactory decoratee;
    protected SparqlViewCache conceptMap;
    protected long indexResultSetSizeThreshold;

    public ViewCacheIndexerImpl(QueryExecutionFactory decoratee, SparqlViewCache conceptMap, long indexResultSetSizeThreshold) {
        //super(decoratee);
        this.decoratee = decoratee;
        this.conceptMap = conceptMap;
        this.indexResultSetSizeThreshold = indexResultSetSizeThreshold;
    }

    /* (non-Javadoc)
     * @see org.aksw.jena_sparql_api.concept_cache.core.ViewCacheIndexer#createQueryExecution(org.apache.jena.sparql.algebra.Op, org.apache.jena.query.Query)
     */
    @Override
    public QueryExecution createQueryExecution(Op indexPattern, Query query) {
        ProjectedQuadFilterPattern pqfp = SparqlCacheUtils.transform(indexPattern);
        if(pqfp == null) {
            throw new RuntimeException("Query is not indexable: " + query);
        }

        Set<Var> indexVars = new HashSet<>(query.getProjectVars());

        QueryExecution result = new QueryExecutionViewCacheFragment(query, pqfp, decoratee, conceptMap, indexVars, indexResultSetSizeThreshold);
        return result;
    }

}