package org.aksw.jena_sparql_api.stmt;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.update.UpdateRequest;

public interface SparqlStmt {
    boolean isQuery();
    boolean isUpdateRequest();

    boolean isUnknown();

    boolean isParsed();

    SparqlStmtUpdate getAsUpdateStmt();
    SparqlStmtQuery getAsQueryStmt();

    QueryParseException getParseException();
    String getOriginalString();

    SparqlStmt clone();

    /**
     * Return the prefix mapping of the query or update request.
     * Only valid if isParsed() is true.
     *
     * @return the prefix mapping
     */
    PrefixMapping getPrefixMapping();

    default Query getQuery() {
        SparqlStmtQuery stmt = getAsQueryStmt();
        Query result = stmt == null ? null : stmt.getQuery();
        return result;
    }

    default UpdateRequest getUpdateRequest() {
        SparqlStmtUpdate stmt = getAsUpdateStmt();
        UpdateRequest result = stmt == null ? null : stmt.getUpdateRequest();
        return result;
    }
}
