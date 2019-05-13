package org.aksw.jena_sparql_api.sparql.ext.init;

import org.aksw.jena_sparql_api.sparql.ext.benchmark.E_Benchmark;
import org.aksw.jena_sparql_api.sparql.ext.csv.JenaExtensionCsv;
import org.aksw.jena_sparql_api.sparql.ext.fs.JenaExtensionFs;
import org.aksw.jena_sparql_api.sparql.ext.geosparql.JenaExtensionsGeoSparql;
import org.aksw.jena_sparql_api.sparql.ext.gml.JenaExtensionGml;
import org.aksw.jena_sparql_api.sparql.ext.json.JenaExtensionJson;
import org.aksw.jena_sparql_api.sparql.ext.sys.JenaExtensionSys;
import org.aksw.jena_sparql_api.sparql.ext.url.JenaExtensionUrl;
import org.aksw.jena_sparql_api.sparql.ext.xml.JenaExtensionXml;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sys.JenaSubsystemLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitJenaSparqlApiSparqlExtensions
    implements JenaSubsystemLifecycle
{
    private static final Logger logger = LoggerFactory.getLogger(InitJenaSparqlApiSparqlExtensions.class);

    public void start() {
        logger.debug("JenaSparqlAPI SPARQL extension initialization");
        
        FunctionRegistry registry = FunctionRegistry.get();
        
        JenaExtensionJson.register();
        JenaExtensionCsv.register();
        JenaExtensionGml.register();
        JenaExtensionXml.register();
        JenaExtensionUrl.register();
        JenaExtensionFs.register();
        JenaExtensionSys.register();
        JenaExtensionsGeoSparql.loadDefs(registry);        
    }
    
    @Override
    public void stop() {
    }
}
