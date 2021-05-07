package org.aksw.jena_sparql_api.sparql.ext.geosparql;

import org.aksw.jena_sparql_api.mapper.proxy.function.FunctionBinder;
import org.aksw.jena_sparql_api.mapper.proxy.function.FunctionGenerator;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.GeoSPARQL_URI;
import org.apache.jena.sparql.expr.aggregate.AggregateRegistry;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.locationtech.jts.geom.Geometry;

public class JenaExtensionsGeoSparqlX {

	public static void register() {
		loadDefs(FunctionRegistry.get());
		
		AggregateRegistry.register(
				GeoSPARQL_URI.GEOF_URI + "collect",
				AccumulatorFactories.wrap1(AggregatorsJena::aggGeometryCollection));
	}		

	public static void loadDefs(FunctionRegistry registry) {
        TypeMapper.getInstance().registerDatatype(RDFDatatypeWkbLiteral.INSTANCE);

        registry.put(GeoSPARQL_URI.GEOF_URI + "wkb2wkt", F_Wkb2Wkt.class);
        registry.put(GeoSPARQL_URI.GEOF_URI + "parsePolyline", F_ParsePolyline.class);
        
        FunctionBinder binder = new FunctionBinder(registry);
        FunctionGenerator generator = binder.getFunctionGenerator();
        
        // Define two-way Geometry - GeometryWrapper coercions
        generator.getConverterRegistry()
        	.register(Geometry.class, GeometryWrapper.class,
        			geometry -> new GeometryWrapper(geometry, WKTDatatype.URI),
        			GeometryWrapper::getParsingGeometry);

        // Declare that the conversion of Geometry to GeometryWrapper
        // yields an RDF-compatible java object w.r.t. Jena's TypeMapper
        binder.getFunctionGenerator().getJavaToRdfTypeMap().put(Geometry.class, GeometryWrapper.class);

        // Map GeometryWrapper to the IRI of the WKT datatype
        // WKTDatatype.getJavaClass() in Jena4 incorrectly returns null instead of GeometryWrapper.class 
        generator.getTypeByClassOverrides().put(GeometryWrapper.class, WKTDatatype.URI);
        

        try {
			binder.register(GeoFunctionsJena.class.getMethod("simplifyDp", Geometry.class, double.class, boolean.class));
		} catch (NoSuchMethodException | SecurityException e) {
        	throw new RuntimeException(e);
		}
        
		// FunctionRegistry.get().put(ns + "nearestPoints", uri -> new E_ST_NearestPoints());		
	}
}
