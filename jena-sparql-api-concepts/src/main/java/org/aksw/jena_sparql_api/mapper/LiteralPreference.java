package org.aksw.jena_sparql_api.mapper;

import java.util.List;

import org.apache.jena.graph.Node;

public class LiteralPreference {
    protected List<String> langs;
    protected List<Node> predicates;
    protected boolean preferProperties = false;

    public LiteralPreference(List<String> langs, List<Node> predicates,
            boolean preferProperties) {
        super();
        this.langs = langs;
        this.predicates = predicates;
        this.preferProperties = preferProperties;
    }

    public List<String> getLangs() {
        return langs;
    }

    public List<Node> getPredicates() {
        return predicates;
    }

    public boolean isPreferProperties() {
        return preferProperties;
    }
}
