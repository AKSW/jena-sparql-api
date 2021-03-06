package org.aksw.jena_sparql_api.batch.json.rewriters;

import java.util.Map.Entry;

import org.aksw.gson.utils.JsonVisitorRewrite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 *
 *
 * @author raven
 *
 */
public class JsonVisitorRewriteSimpleJob
	extends JsonVisitorRewrite
{
	@Override
	public JsonElement visit(JsonObject json) {
		JsonElement result;
		if(json.has("$simpleJob")) {
			JsonObject $sparqlStep = json.get("$simpleJob").getAsJsonObject();

			JsonObject o = new JsonObject();

			o.addProperty("type", "org.aksw.jena_sparql_api.batch.step.FactoryBeanSimpleJob");

			for(Entry<String, JsonElement> entry : $sparqlStep.entrySet()) {
				o.add(entry.getKey(), entry.getValue());
			}


//			JsonObject autwired = new JsonObject();
//			autwired.addProperty("autowired", "byType");
//			o.add("stepBuilders", autwired);

			result = o;
		} else {
			result = json;
		}

		return result;
	}
}