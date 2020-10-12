package org.aksw.jena_sparql_api.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import org.apache.jena.atlas.io.AWriter;
import org.apache.jena.atlas.io.StringWriterI;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.ext.com.google.common.collect.Maps;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.out.NodeFormatterNT;
import org.apache.jena.sparql.core.Var;

import com.google.common.collect.Iterables;

public class NodeUtils {

    public static final String nullUri = "http://null.null/null";
    public static final Node nullUriNode = NodeFactory.createURI(nullUri);

    // Prefix for URIs referring to environment variables
    public static final String ENV_PREFIX = "env:";

//	public static final Node N_ABSENT = NodeFactory.createURI("http://special.absent/none");

    // Note to myself because I repeatedly added node/prefix utils here:
    // Prefix / PrefixMapping related utils are in PrefixUtils ~ Claus

    public static boolean isEnvKey(Node node) {
        boolean result = getEnvKey(node) != null;
        return result;
    }

    // Return key + flag for string/iri
    public static Entry<String, Boolean> getEnvKey(Node node) {
        Entry<String, Boolean> result = null;
        if(node.isURI()) {
            String str = node.getURI();
            if(str.startsWith(ENV_PREFIX)) {
                String key = str.substring(ENV_PREFIX.length());

                boolean isIri = false;
                if(key.startsWith("//")) {
                    key = key.substring(2);
                    isIri = true;
                }

                result = Maps.immutableEntry(key, isIri);
            }
        }

        return result;
    }


    public static Node substWithLookup2(Node node, Function<String, Node> lookup) {
        Entry<String, Boolean> e = getEnvKey(node);

        Node result = node;
        if(e != null) {
            String key = e.getKey();
            boolean isUri = e.getValue();
            Node value = lookup.apply(key);
            if(value != null) {
                result = isUri
                    ? NodeFactory.createURI(value.toString())
                    : value; // NodeFactory.createLiteral(value);
            }
        }

        return result;
    }

    public static Node substWithLookup(Node node, Function<String, String> lookup) {

        Entry<String, Boolean> e = getEnvKey(node);

        Node result = node;
        if(e != null) {
            String key = e.getKey();
            boolean isUri = e.getValue();
            String value = lookup.apply(key);
            if(value != null) {
                result = isUri
                    ? NodeFactory.createURI(value)
                    : NodeFactory.createLiteral(value);
            }

        }

        return result;

//		Node result = node;
//		if(node.isURI()) {
//			String str = node.getURI();
//			if(str.startsWith(ENV_PREFIX)) {
//				String key = str.substring(ENV_PREFIX.length());
//
//				boolean isUri = false;
//				if(key.startsWith("//")) {
//					key = key.substring(2);
//					isUri = true;
//				}
//
//
//				String value = lookup.apply(key);
//				if(!Strings.isNullOrEmpty(value)) {
//					result = isUri
//						? NodeFactory.createURI(value)
//						: NodeFactory.createLiteral(value);
//				}
//			}
//		}
//
//		return result;
    }

    /**
     * Return the language of a node or null if the argument is not applicable
     *
     * @param node
     * @return
     */
    public static String getLang(Node node) {
        String result = node != null && node.isLiteral() ? node.getLiteralLanguage() : null;
        return result;
    }


    public static Node asNullableNode(String uri) {
        Node result = uri == null ? null : NodeFactory.createURI(uri);
        return result;
    }

    public static List<Node> fromUris(Iterable<String> uris) {
        List<Node> result = new ArrayList<Node>(Iterables.size(uris));
        for(String uri : uris) {
            Node node = NodeFactory.createURI(uri);
            result.add(node);
        }
        return result;
    }

    public static Node createTypedLiteral(TypeMapper typeMapper, Object o) {
        Class<?> clazz = o.getClass();
        RDFDatatype dtype = typeMapper.getTypeByClass(clazz);
        String lex = dtype.unparse(o);
        Node result = NodeFactory.createLiteral(lex, dtype);
        return result;
    }

    public static Set<Node> getBnodesMentioned(Iterable<Node> nodes) {
        Set<Node> result = new HashSet<Node>();
        for (Node node : nodes) {
            if (node.isBlank()) {
                result.add(node);
            }
        }

        return result;
    }

    public static Set<Var> getVarsMentioned(Iterable<Node> nodes)
    {
        Set<Var> result = new HashSet<Var>();
        for (Node node : nodes) {
            if (node.isVariable()) {
                result.add((Var)node);
            }
        }

        return result;
    }


    @Deprecated // Use NodeFmtLib.str
    public static String toNTriplesString(Node node) {
        NodeFormatterNT formatter = new NodeFormatterNT();
        AWriter writer = new StringWriterI();
        formatter.format(writer, node);
        String result = writer.toString();
        return result;
    }


    @Deprecated
    public static String toNTriplesStringOld(Node node) {

        String result;
        if(node.isURI()) {
            result = "<" + node.getURI() + ">";
        }
        else if(node.isLiteral()) {
            String lex = node.getLiteralLexicalForm();
            String lang = node.getLiteralLanguage();
            String dt = node.getLiteralDatatypeURI();

            String tmp = lex;
            // \\   \"   \n    \t   \r
            tmp = tmp.replace("\\", "\\\\");
            tmp = tmp.replace("\"", "\\\"");
            tmp = tmp.replace("\n", "\\n");
            tmp = tmp.replace("\t", "\\t");
            tmp = tmp.replace("\r", "\\r");

            String encoded = tmp;
            // If fields contain new lines, escape them with triple quotes
//			String quote = encoded.contains("\n")
//					? "\"\"\""
//					: "\"";
            String quote = "\"";

            result =  quote + encoded + quote;

            if(dt != null && !dt.isEmpty()) {
                result = result + "^^<" + dt+ ">";
            } else {
                if(!lang.isEmpty()) {
                    result = result + "@" + lang;
                }
            }
        }
        else if(node.isBlank()) {
            result = "_:" + node.getBlankNodeLabel();
        } else if(node.isVariable()) {
            result = "?" + ((Var)node).getVarName();
        } else {
            throw new RuntimeException("Cannot serialize [" + node + "] as N-Triples");
        }

        return result;
    }
}