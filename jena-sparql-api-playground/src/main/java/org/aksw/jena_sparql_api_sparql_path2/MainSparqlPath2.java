package org.aksw.jena_sparql_api_sparql_path2;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.aksw.jena_sparql_api.concepts.Concept;
import org.aksw.jena_sparql_api.core.GraphSparqlService;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.core.SparqlService;
import org.aksw.jena_sparql_api.core.SparqlServiceFactory;
import org.aksw.jena_sparql_api.lookup.ListService;
import org.aksw.jena_sparql_api.lookup.ListServiceUtils;
import org.aksw.jena_sparql_api.lookup.LookupService;
import org.aksw.jena_sparql_api.lookup.LookupServiceCacheMem;
import org.aksw.jena_sparql_api.lookup.LookupServiceListService;
import org.aksw.jena_sparql_api.lookup.LookupServicePartition;
import org.aksw.jena_sparql_api.mapper.Agg;
import org.aksw.jena_sparql_api.mapper.AggLiteral;
import org.aksw.jena_sparql_api.mapper.AggMap;
import org.aksw.jena_sparql_api.mapper.AggTransform;
import org.aksw.jena_sparql_api.mapper.BindingMapperProjectVar;
import org.aksw.jena_sparql_api.mapper.MappedQuery;
import org.aksw.jena_sparql_api.stmt.SparqlParserConfig;
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl;
import org.aksw.jena_sparql_api.update.FluentSparqlService;
import org.aksw.jena_sparql_api.update.FluentSparqlServiceFactory;
import org.aksw.jena_sparql_api.utils.DatasetDescriptionUtils;
import org.aksw.jena_sparql_api.utils.ElementUtils;
import org.aksw.jena_sparql_api.utils.TripleUtils;
import org.aksw.jena_sparql_api.utils.Vars;
import org.aksw.jena_sparql_api.web.server.ServerUtils;
import org.apache.jena.atlas.web.auth.HttpAuthenticator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.core.DatasetDescription;
import org.apache.jena.sparql.core.Prologue;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.aggregate.AggCountVarDistinct;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathParser;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class MainSparqlPath2 {

    private static final Logger logger = LoggerFactory.getLogger(MainSparqlPath2.class);

    public static SparqlService wrapSparqlService(SparqlService coreSparqlService, SparqlStmtParserImpl sparqlStmtParser, Prologue prologue) {

        GraphSparqlService graph = new GraphSparqlService(coreSparqlService);
        Model model = ModelFactory.createModelForGraph(graph);

        Context context = ARQ.getContext().copy();

        SparqlService result = FluentSparqlService
                .from(model, context)
                .config()
                    .configQuery()
                        .withParser(sparqlStmtParser.getQueryParser())
                        .withPrefixes(prologue.getPrefixMapping(), true) // If a query object is without prefixes, inject them
                    .end()
                .end()
                .create();


        context.put(PropertyFunctionKShortestPaths.PROLOGUE, prologue);
        context.put(PropertyFunctionKShortestPaths.SPARQL_SERVICE, coreSparqlService);

        return result;
    }

    public static String createPathExprStr(String predicate) {
        String result = "(<" + predicate + ">/(!<http://foo>)*)|(!<http://foo>)*/<" + predicate + ">";
        return result;
    }


    public static LookupService<Node, Map<Node, Number>> createJoinSummaryLookupService(QueryExecutionFactory qef, boolean reverse) {

        Query query = new Query();
        QueryFactory.parse(query, "PREFIX o: <http://example.org/ontology/> SELECT ?x ?y ((<http://www.w3.org/2001/XMLSchema#double>(?fy) / <http://www.w3.org/2001/XMLSchema#double>(?fx)) As ?z) { ?s o:sourcePredicate ?x ; o:targetPredicate ?y ; o:freqSource ?fx ; o:freqTarget ?fy }", "http://example.org/base/", Syntax.syntaxARQ);

        Var source = !reverse ? Vars.x : Vars.y;
        Var target = !reverse ? Vars.y : Vars.x;
        Var freq = Vars.z;



        Agg<Map<Node, Number>> agg = AggMap.create(
                BindingMapperProjectVar.create(target),
                AggTransform.create(AggLiteral.create(BindingMapperProjectVar.create(freq)), (node) -> {
                    Number result;
                    // TODO Make a bug report that sometimes double rdf terms in json serialization in virtuoso 7.2.2 turn up as NAN
                    try {
                        Number n = (Number)node.getLiteralValue();
                        result = reverse ? 1.0 / n.doubleValue() : n;
                    } catch(Exception e) {
                        logger.warn("Not a numeric literal: " + node);
                        result = 1.0;
                    }
                    return result;
                }));
        MappedQuery<Map<Node, Number>> mappedQuery = MappedQuery.create(query, source, agg);

        ListService<Concept, Node, Map<Node, Number>> lsx = ListServiceUtils.createListServiceMappedQuery(qef, mappedQuery, false);
        LookupService<Node, Map<Node, Number>> result = LookupServiceListService.create(lsx);

        result = LookupServicePartition.create(result, 100, 4);
        result = LookupServiceCacheMem.create(result, 20000);

        return result;
    }

    public static JoinSummaryService createJoinSummaryService(QueryExecutionFactory qef) {
        JoinSummaryServiceImpl result = new JoinSummaryServiceImpl(
                createJoinSummaryLookupService(qef, false),
                createJoinSummaryLookupService(qef, true));

        return result;
    }


    //ListService<Concept, Node, List<Node>>
    /**
     * Maps nodes to their predicates and their count of distinct values
     *
     * @param qef
     * @param reverse
     * @return
     */
    public static LookupService<Node, Map<Node, Number>> createListServicePredicates(QueryExecutionFactory qef, boolean reverse) {
        Query query = new Query();
        query.setQuerySelectType();
        query.setDistinct(true);
        query.getProject().add(Vars.s);
        query.getProject().add(Vars.p);
        query.getProject().add(Vars.x, new ExprAggregator(Vars.y, new AggCountVarDistinct(new ExprVar(Vars.o))));
        query.getGroupBy().add(Vars.s);
        query.getGroupBy().add(Vars.p);
        Triple t = new Triple(Vars.s, Vars.p, Vars.o);
        if(reverse) {
            t = TripleUtils.swap(t);
        }

        query.setQueryPattern(
                ElementUtils.createElement(t));


        Agg<Map<Node, Number>> agg = AggMap.create(
                BindingMapperProjectVar.create(Vars.p),
                AggTransform.create(AggLiteral.create(BindingMapperProjectVar.create(Vars.x)), (node) -> (Number)node.getLiteralValue()));
        MappedQuery<Map<Node, Number>> mappedQuery = MappedQuery.create(query, Vars.s, agg);

        ListService<Concept, Node, Map<Node, Number>> lsx = ListServiceUtils.createListServiceMappedQuery(qef, mappedQuery, false);
        LookupService<Node, Map<Node, Number>> result = LookupServiceListService.create(lsx);


        return result;
    }


    public static void main(String[] args) throws InterruptedException {



        PropertyFunctionRegistry.get().put(PropertyFunctionKShortestPaths.DEFAULT_IRI, new PropertyFunctionFactoryKShortestPaths());

        String queryStr;

        DatasetDescription dataset;
        DatasetDescription predDataset;
        DatasetDescription predJoinDataset;
        String pathExprStr;
        Node s;

        if(false) {
            dataset = DatasetDescriptionUtils.createDefaultGraph("http://fp7-pp.publicdata.eu/");
            predDataset = DatasetDescriptionUtils.createDefaultGraph("http://fp7-pp.publicdata.eu/summary/predicate/");
            predJoinDataset = DatasetDescriptionUtils.createDefaultGraph("http://fp7-pp.publicdata.eu/summary/predicate-join/");

            pathExprStr = createPathExprStr("http://fp7-pp.publicdata.eu/ontology/funding");
            s = NodeFactory.createURI("http://fp7-pp.publicdata.eu/resource/project/257943");

            queryStr = "SELECT ?path { <" + s + "> jsafn:kShortestPaths ('" + pathExprStr + "' ?path <http://fp7-pp.publicdata.eu/resource/city/France-PARIS> 471199) }";

        } else {
            dataset = DatasetDescriptionUtils.createDefaultGraph("http://2016.eswc-conferences.org/top-k-shortest-path-large-typed-rdf-graphs-challenge/training_dataset.nt");
            predDataset = DatasetDescriptionUtils.createDefaultGraph("http://2016.eswc-conferences.org/top-k-shortest-path-large-typed-rdf-graphs-challenge/training_dataset.nt/summary/predicate/");
            predJoinDataset = DatasetDescriptionUtils.createDefaultGraph("http://2016.eswc-conferences.org/top-k-shortest-path-large-typed-rdf-graphs-challenge/training_dataset.nt/summary/predicate-join/");

            pathExprStr = createPathExprStr("http://dbpedia.org/ontology/president");
            s = NodeFactory.createURI("http://dbpedia.org/resource/James_K._Polk");
            queryStr = "SELECT ?path { <" + s + "> jsafn:kShortestPaths ('" + pathExprStr + "' ?path <http://dbpedia.org/resource/Felix_Grundy> 471199) }";
        }

        System.out.println("Query string: " + queryStr);

        //SparqlService coreSparqlService = FluentSparqlService.http("http://fp7-pp.publicdata.eu/sparql", "http://fp7-pp.publicdata.eu/").create();
        //SparqlService coreSparqlService = FluentSparqlService.http("http://localhost:8890/sparql", "http://fp7-pp.publicdata.eu/").create();
        //FluentSparqlServiceFactoryFn.start().configService().

        //SparqlService coreSparqlService = FluentSparqlService.http("http://dbpedia.org/sparql", "http://dbpedia.org").create();




//        tripleRdd.mapToPair(new PairFunction<Triple, K2, V2>() {
//            @Override
//            public Tuple2<K2, V2> call(Triple t) throws Exception {
//
//            }
//        });


//              val rs = text.map(NTriplesParser.parseTriple)
//
//              val indexedmap = (rs.map(_._1) union rs.map(_._3)).distinct.zipWithIndex //indexing
//              val vertices: RDD[(VertexId, String)] = indexedmap.map(x => (x._2, x._1))
//              val _iriToId: RDD[(String, VertexId)] = indexedmap.map(x => (x._1, x._2))
//
//              val tuples = rs.keyBy(_._1).join(indexedmap).map({
//                case (k, ((s, p, o), si)) => (o, (si, p))
//              })
//
//              val edges: RDD[Edge[String]] = tuples.join(indexedmap).map({
//                case (k, ((si, p), oi)) => Edge(si, oi, p)
//              })

              // TODO is there a specific reason to not return the graph directly? ~ Claus
              //_graph =
//              Graph(vertices, edges)
//
//              new {
//                val graph = Graph(vertices, edges)
//                val iriToId = _iriToId
//              }



        PrefixMappingImpl pm = new PrefixMappingImpl();
        pm.setNsPrefix("jsafn", "http://jsa.aksw.org/fn/");
        pm.setNsPrefixes(PrefixMapping.Extended);
        Prologue prologue = new Prologue(pm);

        SparqlStmtParserImpl sparqlStmtParser = SparqlStmtParserImpl.create(SparqlParserConfig.create(Syntax.syntaxARQ, prologue));


        SparqlServiceFactory ssf = new SparqlServiceFactory() {
            @Override
            public SparqlService createSparqlService(String serviceUri,
                    DatasetDescription datasetDescription, Object authenticator) {

                SparqlService coreSparqlService = FluentSparqlService.http(serviceUri, datasetDescription, (HttpAuthenticator)authenticator).create();
                SparqlService r = wrapSparqlService(coreSparqlService, sparqlStmtParser, prologue);
                return r;
            }
        };

        ssf = FluentSparqlServiceFactory.from(ssf)
                .configFactory()
                    //.defaultServiceUri("http://dbpedia.org/sparql")
                    .defaultServiceUri("http://localhost:8890/sparql")
                    .configService()
                        .configQuery()
                            //.withPagination(1000)
                        .end()
                    .end()
                .end()
                .create();




        SparqlServiceFactory ssf2 = new SparqlServiceFactory() {
            @Override
            public SparqlService createSparqlService(String serviceUri,
                    DatasetDescription datasetDescription, Object authenticator) {

                SparqlService r = FluentSparqlService.http(serviceUri, datasetDescription, (HttpAuthenticator)authenticator).create();
                //SparqlService r = wrapSparqlService(coreSparqlService, sparqlStmtParser, prologue);
                return r;
            }
        };
        ssf2 = FluentSparqlServiceFactory.from(ssf2)
                .configFactory()
                    .defaultServiceUri("http://localhost:8890/sparql")
                .end()
                .create();



        SparqlService ssps = ssf2.createSparqlService(null, predDataset, null);
        SparqlService sspjs = ssf2.createSparqlService(null, predJoinDataset, null);

//        System.out.println("Loading predicate summary");
//        Map<Node, Long> ps = EdgeReducer.loadPredicateSummary(ssps.getQueryExecutionFactory());
//        System.out.println("Predicate summary is: " + ps.size());
//
//        System.out.println("Loading join summary");
//        BiHashMultimap<Node, Node> pjs = EdgeReducer.loadJoinSummary(sspjs.getQueryExecutionFactory());
//        System.out.println("Done: join summary is " + pjs.size());


        if(true) {
            //ssf.createSparqlService("http://, datasetDescription, authenticator)
            SparqlService ss = ssf.createSparqlService(null, dataset, null);
            QueryExecutionFactory qef = ss.getQueryExecutionFactory();
            //ListService<Concept, Node, List<Node>> lsx =
            //LookupService<Node, List<Node>> ls = LookupServiceListService.create(lsx);
            LookupService<Node, Map<Node, Number>> fwdLs = createListServicePredicates(qef, false);
            LookupService<Node, Map<Node, Number>> bwdLs = createListServicePredicates(qef, true);

            // Fetch the properties for the source and and states
            Map<Node, Map<Node, Number>> fwdPreds = fwdLs.apply(Collections.singleton(s));
            Map<Node, Map<Node, Number>> bwdPreds = bwdLs.apply(Collections.singleton(s));

            Pair<Map<Node, Number>> initPredFreqs =
                    new Pair<>(fwdPreds.getOrDefault(s, Collections.emptyMap()), bwdPreds.getOrDefault(s, Collections.emptyMap()));

            System.out.println(fwdPreds);
            System.out.println(bwdPreds);

            Path path = PathParser.parse(pathExprStr, PrefixMapping.Extended);
            Nfa<Integer, LabeledEdge<Integer, PredicateClass>> nfa = PathExecutionUtils.compileToNfa(path);

            System.out.println("NFA: " + nfa);
            System.out.println(nfa.getStartStates());
            System.out.println(nfa.getEndStates());
            nfa.getGraph().edgeSet().forEach(x -> System.out.println(x));

            JoinSummaryService joinSummaryService = createJoinSummaryService(sspjs.getQueryExecutionFactory());


            JoinSummaryService2 jss2 = new JoinSummaryService2Impl(sspjs.getQueryExecutionFactory());
//            Map<Node, Number> test = jss2.fetchPredicates(Arrays.<Node>asList(NodeFactory.createURI("http://dbpedia.org/property/owner")), false);
//            Map<Node, Number> test = jss2.fetchPredicates(Arrays.<Node>asList(NodeFactory.createURI("http://dbpedia.org/property/novPrecipInch")), true);

//            System.out.println("join summary 2: " + test);

//            Node issue = NodeFactory.createURI("http://dbpedia.org/ontology/owner");
//            Map<Node, Map<Node, Number>> test = joinSummaryService.fetch(Collections.singleton(issue), false);
//            System.out.println("Test: " + test);

            EdgeReducer.<Integer, LabeledEdge<Integer, PredicateClass>>estimateFrontierCost(
                    nfa,
                    LabeledEdgeImpl::isEpsilon,
                    e -> e.getLabel(),
                    initPredFreqs,
                    joinSummaryService);

            // 2. Label the nfa by iterating the nfa backwards

            // 3. For every path in the nfa,


//

//            Map<Node, Number> fwdNodes = new HashSet<>(fwdPreds.get(s));
//            Map<Node, Number> bwdNodes = new HashSet<>(bwdPreds.get(s));
//
//            PredicateClass pc = new PredicateClass(
//                    new ValueSet<Node>(true, fwdNodes),
//                    new ValueSet<Node>(true, bwdNodes));



            boolean execQuery = false;
            if(execQuery) {
                QueryExecution qe = qef.createQueryExecution(queryStr);
                ResultSet rs = qe.execSelect();
                ResultSetFormatter.outputAsJSON(System.out, rs);
            }


        } else {
            Server server = ServerUtils.startSparqlEndpoint(ssf, sparqlStmtParser, 7533);
            server.join();
        }




        // Create a datasetGraph backed by the SPARQL service to DBpedia
//        DatasetGraphSparqlService datasetGraph = new DatasetGraphSparqlService(coreSparqlService);

        // TODO Add support for sparqlService transformation
//        final SparqlServiceFactory ssf = FluentSparqlServiceFactory.from(new SparqlServiceFactoryHttp())
//            .configFactory()
//                .defaultServiceUri("http://dbpedia.org/sparql")
//                .configService()
//                    .configQuery()
//                        .withParser(sparqlStmtParser.getQueryParser())
//                        .withPrefixes(pm, true) // If a query object is without prefixes, inject them
//                    .end()
//                .end()
//            .end()
//            .create();



//        SparqlServiceFactory ssf = new SparqlServiceFactory() {
//            @Override
//            public SparqlService createSparqlService(String serviceUri,
//                    DatasetDescription datasetDescription,
//                    Object authenticator) {
//                return sparqlService;
//            }
//
//        };



        //Model model = ModelFactory.createDefaultModel();
        //GraphQueryExecutionFactory

        //String queryStr = "SELECT * { ?s ?p ?o } LIMIT 10";
//
        //String queryStr = "SELECT ?path { <http://fp7-pp.publicdata.eu/resource/project/257943> jsafn:kShortestPaths ('(rdf:type|!rdf:type)*' ?path <http://fp7-pp.publicdata.eu/resource/city/France-PARIS>) }";
//        String queryStr = "SELECT ?path { <http://fp7-pp.publicdata.eu/resource/project/257943> jsafn:kShortestPaths ('rdf:type*' ?path) }";
//        //QueryExecutionFactory qef = FluentQueryExecutionFactory.http("http://dbpedia.org/sparql", "http://dbpedia.org").create();
//
//        for(int i = 0; i < 1; ++i) {
//            QueryExecutionFactory qef = sparqlService.getQueryExecutionFactory();
//            QueryExecution qe = qef.createQueryExecution(queryStr);
////            //System.out.println("query: " + qe.getQuery());
//            System.out.println("Result");
//            ResultSet rs = qe.execSelect();
//            System.out.println(ResultSetFormatter.asText(rs));
//            //ResultSetFormatter.outputAsTSV(System.out, rs);
//        }

      //Thread.sleep(1000);
    }


}
