package org.aksw.jena_sparql_api.stmt;

import com.google.common.base.Function;
import org.apache.jena.query.Query;

public interface SparqlQueryParser
    extends Function<String, Query>
{
}