package org.aksw.jena_sparql_api.stmt;

import com.google.common.base.Function;
import org.apache.jena.update.UpdateRequest;

public interface SparqlUpdateParser
    extends Function<String, UpdateRequest>
{
}