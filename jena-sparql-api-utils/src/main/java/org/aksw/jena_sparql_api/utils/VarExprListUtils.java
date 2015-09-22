package org.aksw.jena_sparql_api.utils;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.core.VarExprList;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprVars;
import com.hp.hpl.jena.sparql.syntax.PatternVars;

public class VarExprListUtils {
    /**
     * Get the referenced variables
     *
     * @param vel
     * @return
     */
    public static Set<Var> getRefVars(VarExprList vel) {
        Set<Var> result = new HashSet<Var>();

        for(Entry<Var, Expr> entry : vel.getExprs().entrySet()) {
            if(entry.getValue() == null) {
                result.add(entry.getKey());
            } else {
                Set<Var> vs = ExprVars.getVarsMentioned(entry.getValue());
                result.addAll(vs);
            }
        }
        return result;
    }

}