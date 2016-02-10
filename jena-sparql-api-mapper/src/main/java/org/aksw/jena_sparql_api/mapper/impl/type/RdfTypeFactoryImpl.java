package org.aksw.jena_sparql_api.mapper.impl.type;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.aksw.commons.util.strings.StringUtils;
import org.aksw.jena_sparql_api.mapper.annotation.DefaultIri;
import org.aksw.jena_sparql_api.mapper.annotation.Iri;
import org.aksw.jena_sparql_api.mapper.annotation.IriType;
import org.aksw.jena_sparql_api.mapper.annotation.MappedBy;
import org.aksw.jena_sparql_api.mapper.annotation.MultiValued;
import org.aksw.jena_sparql_api.mapper.model.F_GetValue;
import org.aksw.jena_sparql_api.mapper.model.RdfPopulatorProperty;
import org.aksw.jena_sparql_api.mapper.model.RdfPopulatorPropertyMulti;
import org.aksw.jena_sparql_api.mapper.model.RdfPopulatorPropertySingle;
import org.aksw.jena_sparql_api.mapper.model.RdfType;
import org.aksw.jena_sparql_api.mapper.model.RdfTypeFactory;
import org.aksw.jena_sparql_api.stmt.SparqlRelationParser;
import org.aksw.jena_sparql_api.stmt.SparqlRelationParserImpl;
import org.aksw.jena_sparql_api.util.frontier.Frontier;
import org.aksw.jena_sparql_api.util.frontier.FrontierImpl;
import org.aksw.jena_sparql_api.utils.UriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import com.google.common.base.Function;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Prologue;

public class RdfTypeFactoryImpl
    implements RdfTypeFactory
{

    private static final Logger logger = LoggerFactory.getLogger(RdfTypeFactoryImpl.class);

    /*
     * SpEL parser and evaluator
     */
    protected ExpressionParser parser;
    protected EvaluationContext evalContext;
    protected ParserContext parserContext;

    protected Prologue prologue;
    protected SparqlRelationParser relationParser;

    protected Map<Class<?>, RdfType> classToMapping = new HashMap<Class<?>, RdfType>();
    protected TypeMapper typeMapper;

    public RdfTypeFactoryImpl(ExpressionParser parser, ParserContext parserContext, EvaluationContext evalContext, TypeMapper typeMapper, Prologue prologue, SparqlRelationParser relationParser) {
        super();
        this.parser = parser;
        this.evalContext = evalContext;
        this.parserContext = parserContext;
        this.typeMapper = typeMapper;
        this.prologue = prologue;
        this.relationParser = relationParser;
    }

    public Prologue getPrologue() {
        return prologue;
    }


    @Override
    public RdfType forJavaType(Class<?> clazz) {
        RdfType result = getOrAllocate(clazz);

        if(result instanceof RdfClass) {
            RdfClass tmp = (RdfClass)result;
            populateClasses(tmp);
        }

        return result;
    }

//    public RdfClass create(Class<?> clazz) {
//        RdfClass result;
//        try {
//            result = _create(clazz);
//        } catch (IntrospectionException e) {
//            throw new RuntimeException(e);
//        }
//        return result;
//    }

    //public static <A extends Annotation> A findClassAnnotation(Class<>)

    public static <A extends Annotation> A findPropertyAnnotation(Class<?> clazz, PropertyDescriptor pd, Class<A> annotation) {
        A result;

        String propertyName = pd.getName();
        Field f = ReflectionUtils.findField(clazz, propertyName);
        result = f != null
                ? f.getAnnotation(annotation)
                : null
                ;

        result = result == null && pd.getReadMethod() != null
                ? AnnotationUtils.findAnnotation(pd.getReadMethod(), annotation)
                : result
                ;

        result = result == null && pd.getWriteMethod() != null
                ? AnnotationUtils.findAnnotation(pd.getWriteMethod(), annotation)
                : result
                ;

        return result;
    }


    /**
     * Allocates a new RdfClass object for a given java class or returns an
     * existing one. Does not populate property descriptors.
     *
     * @param clazz
     * @return
     */
    protected RdfType getOrAllocate(Class<?> clazz) {
        RdfType result = classToMapping.get(clazz);
        if(result == null) {
            result = allocate(clazz);
            classToMapping.put(clazz, result);
        }
        return result;
    }

    protected RdfType allocate(Class<?> clazz) {
        RDFDatatype dtype = typeMapper.getTypeByClass(clazz);
        boolean isPrimitive = dtype != null;

        RdfType result;
        if(isPrimitive) {
            result = new RdfTypeLiteralTyped(this, dtype);
        } else {
            result = allocateClass(clazz);
        }

        return result;
    }


    /**
     * Allocates a new RdfClass object for a given java class.
     * Does not populate property descriptors.
     *
     * @param clazz
     * @return
     */
    protected RdfClass allocateClass(Class<?> clazz) {
        org.aksw.jena_sparql_api.mapper.annotation.RdfType rdfType = AnnotationUtils.findAnnotation(clazz, org.aksw.jena_sparql_api.mapper.annotation.RdfType.class);

        DefaultIri defaultIri = AnnotationUtils.findAnnotation(clazz, DefaultIri.class);

        Function<Object, String> defaultIriFn = null;
        if (defaultIri != null) {
            String iriStr = defaultIri.value();
            Expression expression = parser.parseExpression(iriStr,
                    parserContext);
            defaultIriFn = new F_GetValue<String>(String.class, expression,
                    evalContext);
        }

        RdfClass result = new RdfClass(this, clazz, defaultIriFn);

        return result;
    }

    protected String resolveIriExpr(String exprStr) {
        Expression expression = parser.parseExpression(exprStr, parserContext);
        String tmp = expression.getValue(evalContext, String.class);
        tmp = tmp.trim();

        PrefixMapping prefixMapping = prologue.getPrefixMapping();
        String result = prefixMapping.expandPrefix(tmp);
        return result;
    }


//    protected RdfClass _create(Class<?> clazz) throws IntrospectionException {
//        RdfClass result = allocate(clazz);
//        populateClasses(result);
//        //Map<String, RdfProperty> rdfProperties = processProperties(clazz);
//
//        //RdfClassImpl result = new RdfClassImpl(clazz, defaultIriFn, rdfProperties, prologue);
//        return result;
//    }

    private void populateClasses(RdfClass rootRdfClass) {
        Frontier<RdfClass> frontier = FrontierImpl.createIdentityFrontier();

        frontier.add(rootRdfClass);
        while(!frontier.isEmpty()) {
            RdfClass rdfClass = frontier.next();
            initProperties(rdfClass, frontier);
        }

    }


    private void initProperties(RdfClass rdfClass, Frontier<RdfClass> frontier) {
        if(!rdfClass.isPopulated()) {
            //Map<String, RdfPopulatorProperty> rdfProperties = new LinkedHashMap<String, RdfPopulatorProperty>();

            Class<?> clazz = rdfClass.getEntityClass();
            logger.debug("Initializing RdfClass for [" + clazz.getName() + "]");

//            if(Iterable.class.isAssignableFrom(clazz)) {
//                System.out.println("Collection type detected");
//                throw new RuntimeException("Implement me");
//            }


            BeanWrapper beanInfo = new BeanWrapperImpl(clazz);
            //BeanInfo beanInfo = Introspector.getBeanInfo(clazz);

            PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
            for(PropertyDescriptor pd : pds) {
                //String propertyName = pd.getName();
                processProperty(rdfClass, beanInfo, pd, frontier);
//                RdfPopulatorProperty rdfPopulator = processProperty(rdfClass, beanInfo, pd, open);
//
//                if(rdfPopulator != null) {
//                	rdfClass.addPopulator(rdfPopulator);
//                    //rdfProperties.put(propertyName, rdfProperty);
//                }

            }

            //rdfClass.propertyToMapping = rdfProperties;
        }
        rdfClass.setPopulated(true);
    }


    //RdfPopulatorProperty
    protected void processProperty(RdfClass rdfClass, BeanWrapper beanInfo, PropertyDescriptor pd, Frontier<RdfClass> frontier) {
        RdfPopulatorProperty result = null;

        //processProperty(beanInfo, pd, predicate, targetRdfType, open);
        Class<?> clazz = beanInfo.getWrappedClass();

        String propertyName = pd.getName();
        boolean isReadable = beanInfo.isReadableProperty(propertyName);
        boolean isWritable = beanInfo.isWritableProperty(propertyName);


//        RDFDatatype dtype = typeMapper.getTypeByClass(propertyType);
        boolean isCandidate = isReadable && isWritable;


        Iri iriAnn = findPropertyAnnotation(clazz, pd, Iri.class);
        String iriExprStr = iriAnn == null ? null : iriAnn.value();
        String iriStr = iriExprStr == null ? null : resolveIriExpr(iriExprStr);
        boolean hasIri = iriStr != null && !iriStr.isEmpty();

        String mappedBy = (String)AnnotationUtils.getValue(findPropertyAnnotation(clazz, pd, MappedBy.class));
        boolean isMappedBy = mappedBy != null;


        System.out.println("MappedBy " + mappedBy);


        if(isCandidate && (hasIri || isMappedBy)) {
            logger.debug("Annotation on property " + propertyName + " detected: " + iriStr);

            Node predicate = iriStr != null ? NodeFactory.createURI(iriStr) : null;

            processProperty(rdfClass, beanInfo, pd, predicate, frontier);

//            result = isClass
//                ? processDatatypeProperty(beanInfo, pd, predicate, dtype)
//                : processObjectProperty(beanInfo, pd, predicate, open)
//                ;

        } else {
            logger.debug("Ignoring property " + propertyName);
        }


        //return result;
    }

    public static Class<?> extractItemType(Type genericType) {
        Class<?> result = null;
        if(genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType)genericType;
            java.lang.reflect.Type[] types = pt.getActualTypeArguments();
            if(types.length == 1) {
                result = (Class<?>)types[0];
            }

        }

        return result;
    }

//    public static <@Test T> void foobar() {
//
//    }

    protected void processProperty(RdfClass rdfClass, BeanWrapper beanInfo, PropertyDescriptor pd, Node predicate, Frontier<RdfClass> frontier) {
        Class<?> entityClazz = beanInfo.getWrappedClass();

        String propertyName = pd.getName();

        Class<?> propertyType = pd.getPropertyType();


        boolean isCollectionProperty = Collection.class.isAssignableFrom(propertyType);
        boolean isMultiValued = findPropertyAnnotation(entityClazz, pd, MultiValued.class) != null;

        if(isMultiValued && !isCollectionProperty) {
            throw new RuntimeException("Invalid annotation: " + beanInfo.getWrappedClass() + "." + pd.getName() + ": " + " is declared MultiValued however is not a Collection");
        }

        if(isCollectionProperty) {
            Type paramType = pd.getWriteMethod().getGenericParameterTypes()[0];
            //Class<?> itemType = extractItemType(paramType);
            propertyType = extractItemType(paramType);
            //targetRdfType = getOrAllocate(itemType);
        }


        RDFDatatype dtype = typeMapper.getTypeByClass(propertyType);
        boolean isLiteral = dtype != null;

        IriType iriType = findPropertyAnnotation(entityClazz, pd, IriType.class);
        RdfType targetRdfType;
        if(iriType == null) {
            if(isLiteral) {
                targetRdfType = new RdfTypeLiteralTyped(this, dtype);
            } else {
              targetRdfType = getOrAllocate(propertyType);
              if(targetRdfType instanceof RdfClass) {
                  RdfClass tmp = (RdfClass)targetRdfType;
                  if(!tmp.isPopulated()) {
                      frontier.add(tmp);
                  }
              }
            }
        } else {
            Assert.isTrue(String.class.isAssignableFrom(propertyType));
            targetRdfType = new RdfTypeIriStr(this);
        }

        //Relation relation = RelationUtils.createRelation(predicate.getURI(), false, prefixMapping);

        //RdfProperty result = new RdfPropertyDatatypeOld(beanInfo, pd, null, predicate, rdfValueMapper);
        RdfPropertyDescriptor descriptor = new RdfPropertyDescriptor(propertyName, targetRdfType, "");
        RdfPopulatorProperty populator = isCollectionProperty
                ? new RdfPopulatorPropertyMulti(propertyName, predicate, targetRdfType)
                : new RdfPopulatorPropertySingle(propertyName, predicate, targetRdfType)
                ;

        rdfClass.addPropertyDescriptor(descriptor);
        rdfClass.addPopulator(populator);

        //return result;

    }
//
//    /**
//     *
//     *
//     * @param beanInfo
//     * @param pd
//     * @param dtype
//     * @return
//     */
//    protected RdfProperty processDatatypeProperty(BeanWrapper beanInfo, PropertyDescriptor pd, Node predicate, RDFDatatype dtype) {
//        PrefixMapping prefixMapping = prologue.getPrefixMapping();
//
//        Class<?> beanClass = beanInfo.getWrappedClass();
//        Class<?> propertyType = pd.getPropertyType();
//        String propertyName = pd.getName();
//
//        IriType iriType = getAnnotation(beanClass, pd, IriType.class);
//
//
//        //RdfValueMapper rdfValueMapper;
//        org.aksw.jena_sparql_api.mapper.model.RdfType targetType;
//        if(iriType == null) {
//            //RDFDatatype dtype = typeMapper.getTypeByClass(propertyType);
//            //rdfValueMapper = new RdfValueMapperSimple(propertyType, dtype, null);
//            targetType = new RdfTypeLiteralTyped(dtype);
//        } else {
//            //rdfValueMapper = new RdfValueMapperStringIri();
//            targetType = new RdfTypeIriStr();
//        }
//
//
//        Relation relation = RelationUtils.createRelation(predicate.getURI(), false, prefixMapping);
//
//
//        //RdfProperty result = new RdfPropertyDatatypeOld(beanInfo, pd, null, predicate, rdfValueMapper);
//        RdfProperty result = new RdfPropertyImpl(propertyName, relation, targetType);
//        return result;
//    }

    /**
     * Process a property with a complex value
     *
     * @param beanInfo
     * @param pd
     * @param open
     * @return
     */
//    protected RdfProperty processObjectProperty(BeanWrapper beanInfo, PropertyDescriptor pd, Node predicate, Collection<RdfClass> open) {
//        PrefixMapping prefixMapping = prologue.getPrefixMapping();
//
//        RdfProperty result;
//
//        String propertyName = pd.getName();
//        //System.out.println("PropertyName: " + propertyName);
//
//
//        // If necessary, add the target class to the set of classes that yet
//        // need to be populated
//        Class<?> targetClass = pd.getPropertyType();
//        RdfType trc = getOrAllocate(targetClass);
//        if(trc instanceof RdfClass) {
//            RdfClass tmp = (RdfClass)trc;
//            if(!tmp.isPopulated()) {
//                open.add(tmp);
//            }
//        }
//
//        Relation relation = RelationUtils.createRelation(predicate.getURI(), false, prefixMapping);
//        result = new RdfPropertyImpl(propertyName, relation, trc);
//
//
////        Iri iri = getAnnotation(sourceClass, pd, Iri.class);
////        if(iri != null) {
////            String iriStr = iri.value();
////
////            //Relation relation = relationParser.apply(iriStr);
////            Relation relation = RelationUtils.createRelation(iriStr, false, prefixMapping);
////            result = new RdfProperyObject(propertyName, relation, trc);
////
////            logger.debug("Annotation on property " + propertyName + " detected: " + iri.value());
////        } else {
////            result = null;
////            logger.debug("Ignoring property " + propertyName);
////            //throw new RuntimeException("should not happen");
////        }
//
//        return result;
//    }

    public static RdfTypeFactoryImpl createDefault() {
        Prologue prologue = new Prologue();
        RdfTypeFactoryImpl result = createDefault(prologue);
        return result;
    }

    public static RdfTypeFactoryImpl createDefault(Prologue prologue) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        TemplateParserContext parserContext = new TemplateParserContext();

        try {
            evalContext.registerFunction("md5", StringUtils.class.getDeclaredMethod("md5Hash", new Class[] { String.class }));
            evalContext.registerFunction("localName", UriUtils.class.getDeclaredMethod("getLocalName", new Class[] { String.class }));
            evalContext.registerFunction("nameSpace", UriUtils.class.getDeclaredMethod("getNameSpace", new Class[] { String.class }));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ExpressionParser parser = new SpelExpressionParser();

        SparqlRelationParser relationParser = SparqlRelationParserImpl.create(Syntax.syntaxARQ, prologue);

        TypeMapper typeMapper = TypeMapper.getInstance();
        RdfTypeFactoryImpl result = new RdfTypeFactoryImpl(parser, parserContext, evalContext, typeMapper, prologue, relationParser);
        return result;
    }
}