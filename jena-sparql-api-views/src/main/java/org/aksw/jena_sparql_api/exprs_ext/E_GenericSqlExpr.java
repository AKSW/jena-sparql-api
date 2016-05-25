package org.aksw.jena_sparql_api.exprs_ext;

import java.util.Arrays;
import java.util.List;

import org.aksw.sparqlify.algebra.sparql.transform.SqlFunctionDefinition;
import org.apache.commons.lang.NotImplementedException;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprFunctionN;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;

public class E_GenericSqlExpr
	extends ExprFunctionN 
{
	private SqlFunctionDefinition funcDef;
	
	// TODO Instances of this class are instances of function invocations. Add a pointer to the function definition.
	
    //private String sqlFunctionName;
    
    @SuppressWarnings("unchecked")
	public E_GenericSqlExpr(SqlFunctionDefinition funcDef, Expr ... args) {
    	this(funcDef, new ExprList(Arrays.asList(args))) ;
    }

    public E_GenericSqlExpr(SqlFunctionDefinition funcDef, ExprList args) {
    	super(funcDef.getName(), args);
    	this.funcDef = funcDef;
    }
	
    public SqlFunctionDefinition getFuncDef()
    {
    	return funcDef;
    }
    
	@Override
    public NodeValue eval(List<NodeValue> args) {
		// TODO Invoke on some global registry
		throw new NotImplementedException();
	}

	@Override
    public Expr copy(ExprList newArgs) {
		return new E_GenericSqlExpr(funcDef, newArgs);
	}
}
