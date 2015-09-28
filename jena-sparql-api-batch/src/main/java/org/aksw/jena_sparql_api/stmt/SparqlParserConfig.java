package org.aksw.jena_sparql_api.stmt;

import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.sparql.core.Prologue;

public class SparqlParserConfig {
    protected Syntax syntax;
    protected Prologue prologue;

    public SparqlParserConfig(Syntax syntax, Prologue prologue) {
        super();
        this.syntax = syntax;
        this.prologue = prologue;
    }

    public Syntax getSyntax() {
        return syntax;
    }

    public Prologue getPrologue() {
        return prologue;
    }

    public static SparqlParserConfig create() {
        SparqlParserConfig result = create(Syntax.syntaxARQ);
        return result;
    }

    public static SparqlParserConfig create(Syntax syntax) {
        SparqlParserConfig result = create(syntax, new Prologue());
        return result;
    }

    public static SparqlParserConfig create(Syntax syntax, Prologue prologue) {
        SparqlParserConfig result = new SparqlParserConfig(syntax, prologue);
        return result;
    }
}