package org.aksw.jena_sparql_api.sparql.ext.datatypes;

import org.aksw.jena_sparql_api.mapper.proxy.function.FunctionBinder;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.function.FunctionRegistry;

public class JenaExtensionDuration {

    public static final String NS = "http://jsa.aksw.org/fn/duration/";

    public static void register() {
        loadDefs(FunctionRegistry.get());
    }

    public static void loadDefs(FunctionRegistry registry) {
        FunctionBinder binder = new FunctionBinder(registry);
        binder.registerAll(DurationFunctionsJena.class);

//        FunctionGenerator generator = binder.getFunctionGenerator();
//        generator.getConverterRegistry()
//                .register(javax.xml.datatype.Duration.class, java.time.Duration.class,
//                        dur -> java.time.Duration.parse(dur.toString()) ,
//                        GeometryWrapper::getParsingGeometry)
    }

    public static void addPrefixes(PrefixMapping pm) {
        pm.setNsPrefix("duration", NS);
    }
}