package org.aksw.jena_sparql_api.utils.model;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Objects;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

/**
 * NodeMapper based on an RDFDatatype
 * 
 * @author raven Apr 9, 2018
 *
 * @param <T>
 */
public class NodeMapperRdfDatatype<T>
	implements NodeMapper<T>
{
	protected RDFDatatype dtype;
	
	public NodeMapperRdfDatatype(RDFDatatype dtype) {
		super();
		this.dtype = dtype;
	}
	
	@Override
	public boolean canMap(Node node) {
		// TODO we could make use of spring's conversion service to allow implicit conversions (e.g. int -> long)
		boolean result = canMapCore(node, dtype);
		return result;
	}
	
	public static boolean canMapCore(Node node, RDFDatatype dtype) {
		// TODO we could make use of spring's conversion service to allow implicit conversions (e.g. int -> long)
		boolean result = node.isLiteral() && dtype.isValidValue(node.getLiteralValue());
		return result;		
	}
	
	@Override
	public Node toNode(T obj) {
		String lex = dtype.unparse(obj);
		Node result = NodeFactory.createLiteral(lex, dtype);
		return result;
	}

	@Override
	public T toJava(Node node) {
		T result = toJavaCore(node, dtype);
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <T> T toJavaCore(Node node, RDFDatatype dtype) {
		Object obj;
		Class<?> javaClass = dtype.getJavaClass();
		
		/*
		 *  Unfortunately, Jena always narrows the type of numbers:
		 *  Even if e.g. the dtype.getJavaClass() reports a Long, dtype.parse() may yield Integer
		 *  
		 *  TODO Using reflection and parsing strings is pretty much the slowest approach to number conversion
		 */
		if(Number.class.isAssignableFrom(javaClass)) {
			String lex = node.getLiteralLexicalForm();
			if(javaClass.equals(BigDecimal.class)) {
				obj = new BigDecimal(lex);
			} else {
				Method m;
				try {
					try {
						m = javaClass.getMethod("valueOf", String.class);
					} catch(Exception e) {
						throw new RuntimeException("No 'valueOf' method found on Numeric for parsing " + node + " against java type " + javaClass + " based on RDF type " + dtype);
					}
					obj = m.invoke(null, lex);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		} else {
			obj = node.getLiteralValue();
		}

		return (T)obj;		
	}


	@Override
	public Class<?> getJavaClass() {
		Class<?> result = dtype.getJavaClass();
		return result;
	}
}