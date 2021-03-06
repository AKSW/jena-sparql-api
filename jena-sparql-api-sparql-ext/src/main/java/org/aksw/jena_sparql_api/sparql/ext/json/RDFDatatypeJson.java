package org.aksw.jena_sparql_api.sparql.ext.json;

import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;



public class RDFDatatypeJson
    extends BaseDatatype
{
	private static final Logger logger = LoggerFactory.getLogger(RDFDatatypeJson.class);
	
	public static final String IRI = XSD.getURI() + "json"; 
	public static final RDFDatatypeJson INSTANCE = new RDFDatatypeJson();
	
    private Gson gson;

    public RDFDatatypeJson() {
    	this(IRI);
    }

    // Workaround for spark's old 
    public static Gson createGson() {
    	GsonBuilder builder = new GsonBuilder();
    	try {
    		builder.setLenient();
    	} catch(NoSuchMethodError e) {
    		logger.warn("Gson.setLenient not available");
    	}
    	Gson result = builder.create();
    	return result;
    }
    
    public RDFDatatypeJson(String uri) {
        this(uri, createGson());
    }

    public RDFDatatypeJson(String uri, Gson gson) {
        super(uri);
        this.gson = gson;
    }

    @Override
    public Class<?> getJavaClass() {
        return JsonElement.class;
    }

    /**
     * Convert a value of this datatype out
     * to lexical form.
     */
    @Override
    public String unparse(Object value) {
        String result = gson.toJson(value);
        return result;
    }

    /**
     * Parse a lexical form of this datatype to a value
     * @throws DatatypeFormatException if the lexical form is not legal
     */
    @Override
    public JsonElement parse(String lexicalForm) throws DatatypeFormatException {
    	//Object result = gson.fromJson(lexicalForm, Object.class);
    	JsonElement result;
    	try {
    		result = gson.fromJson(lexicalForm, JsonElement.class);
    	} catch(Exception e) {
    		// TODO This is not the best place for an expr eval exception; it should go to E_StrDatatype
    		throw new ExprEvalException(e);
    	}
        return result;
    }
    
	public static Node jsonToNode(JsonElement json) {
		RDFDatatype dtype = RDFDatatypeJson.INSTANCE;
		Node result = NodeFactory.createLiteralByValue(json, dtype);
		return result;
	}

	public static NodeValue jsonToNodeValue(JsonElement json) {
		Node node = jsonToNode(json);
		NodeValue result = NodeValue.makeNode(node);
		return result;
	}
}