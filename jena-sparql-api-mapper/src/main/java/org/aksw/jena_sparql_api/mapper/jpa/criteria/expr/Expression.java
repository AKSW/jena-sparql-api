package org.aksw.jena_sparql_api.mapper.jpa.criteria.expr;

public interface Expression<T>
	extends javax.persistence.criteria.Expression<T>
{
	<X> X accept(ExpressionVisitor<X> visitor);
	<X> Expression<X> as(Class<X> type);
}
