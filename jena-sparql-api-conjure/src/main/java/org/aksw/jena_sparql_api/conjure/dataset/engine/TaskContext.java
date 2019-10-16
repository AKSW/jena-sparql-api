package org.aksw.jena_sparql_api.conjure.dataset.engine;

import java.util.Map;

import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

/**
 * TODO Probably this class should also be turned into a Resource
 * 
 * @author raven
 *
 */
public class TaskContext {
	// TODO Clarify whether we need the input record as a resource or rather as a model
	// in ctxDatasets
	protected Resource inputRecord;
	protected Map<String, DataRef> dataRefMapping;
	
	/**
	 * Context models; right now this is only the input record, but
	 * it allows for extension with other models should the need arise
	 * 
	 */
	protected Map<String, Model> ctxModels;
	
	public TaskContext(
			Resource inputRecord,
			Map<String, DataRef> dataRefMapping,
			Map<String, Model> ctxModels) {
		super();
		this.inputRecord = inputRecord;
		this.dataRefMapping = dataRefMapping;
		this.ctxModels = ctxModels;
	}

	public Resource getInputRecord() {
		return inputRecord;
	}

	public Map<String, DataRef> getDataRefMapping() {
		return dataRefMapping;
	}

	public Map<String, Model> getCtxModels() {
		return ctxModels;
	}
}