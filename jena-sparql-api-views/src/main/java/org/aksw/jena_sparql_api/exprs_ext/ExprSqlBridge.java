package org.aksw.jena_sparql_api.exprs_ext;

import org.aksw.sparqlify.algebra.sql.exprs.SqlExpr;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprFunction0;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;

/**
 * @deprecated
 * There is no point in having a single sql expression being treated as a sparql expression.
 * Use E_RdfTerm2 instead, where the type (uri, literal, bn), value, datatype and language can
 * be specified.
 *
 * @author raven
 *
 */
@Deprecated
public class ExprSqlBridge
    extends ExprFunction0
{
    private SqlExpr sqlExpr;

    public ExprSqlBridge(SqlExpr sqlExpr) {
        super(ExprSqlBridge.class.toString());
        this.sqlExpr = sqlExpr;
    }

    public SqlExpr getSqlExpr() {
        return sqlExpr;
    }

    @Override
    public NodeValue eval(FunctionEnv env) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Expr copy() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String toString()
    {
        return "ExprSqlBridge(" + sqlExpr.toString() + ")";
    }

}