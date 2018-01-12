package net.lecousin.framework.web.services.rest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.io.text.BufferedWritableCharacterStream;
import net.lecousin.framework.json.JSONSpecWriter;
import net.lecousin.framework.json.JSONWriter;
import net.lecousin.framework.network.http.HTTPRequest.Method;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.rest.RESTOperationDescription.Parameter;

public class SwaggerSpecificationPlugin implements RESTSpecificationPlugin {

	@Override
	public String getId() {
		return "swagger-json";
	}
	
	@Override
	public String getName() {
		return "Swagger 3.0.0 (OpenAPI) - JSON";
	}
	
	@Override
	public String getContentType() {
		return "application/json;charset=utf-8";
	}
	
	@Override
	public ISynchronizationPoint<Exception> generate(RESTWebServiceProvider provider, IO.Writable out, WebRequest request) {
		SynchronizationPoint<Exception> result = new SynchronizationPoint<>();
		new Task.Cpu.FromRunnable("Generating JSON OpenAPI 3.0.0", Task.PRIORITY_NORMAL, () -> {
			try {
				BufferedWritableCharacterStream bout = new BufferedWritableCharacterStream(out, StandardCharsets.UTF_8, 8192);
				JSONWriter writer = new JSONWriter(bout, true);
				writer.openObject();
				writer.addObjectAttribute("openapi", "3.0.0");

				writer.addObjectAttribute("info");
				writer.openObject();
				writer.addObjectAttribute("title", "TODO"); // TODO
				writer.addObjectAttribute("version", "TODO"); // TODO
				writer.closeObject();
				
				writer.addObjectAttribute("servers");
				writer.openArray();
				writer.startNextArrayElement();
				writer.openObject();
				WebService.Description d = provider.getWebService().getClass().getAnnotation(WebService.Description.class);
				if (d != null)
					writer.addObjectAttribute("description", d.value());
				writer.addObjectAttribute("url", request.getRootURL() + request.getCurrentPath());
				writer.closeObject();
				writer.closeArray();
				
				writer.addObjectAttribute("paths");
				writer.openObject();
				List<RESTOperationDescription> ops = provider.getOperationsDescriptions();
				Map<String, Map<Method, RESTOperationDescription>> mapping = new HashMap<>();
				for (RESTOperationDescription op : ops) {
					Map<Method, RESTOperationDescription> methods = mapping.get(op.path);
					if (methods == null) {
						methods = new HashMap<>();
						mapping.put(op.path, methods);
					}
					methods.put(op.httpMethod, op);
				}
				ArrayList<String> paths = new ArrayList<>(mapping.keySet());
				Collections.sort(paths);
				for (String path : paths) {
					writer.addObjectAttribute(path);
					writer.openObject();
					for (Map.Entry<Method, RESTOperationDescription> e : mapping.get(path).entrySet()) {
						writer.addObjectAttribute(e.getKey().name().toLowerCase());
						writer.openObject();
						RESTOperationDescription op = e.getValue();
						if (op.description != null)
							writer.addObjectAttribute("summary", op.description);
						writer.addObjectAttribute("operationId", op.restMethod.getDeclaringClass().getName() + '#' + op.restMethod.getName());
						if (!op.parameters.isEmpty()) {
							writer.addObjectAttribute("parameters");
							writer.openArray();
							for (Parameter p : op.parameters) {
								writer.startNextArrayElement();
								writer.openObject();
								writer.addObjectAttribute("name", p.name);
								writer.addObjectAttribute("required", p.required);
								writer.addObjectAttribute("schema");
								writer.openObject();
								new JSONSpecWriter(writer).specifyValue(null, new TypeDefinition(p.type), provider.getParent().getSerializationRules()).blockThrow(0);
								writer.closeObject();
							}
							writer.closeArray();
						}
						if (op.bodyType != null) {
							writer.addObjectAttribute("requestBody");
							writer.openObject();
							writer.addObjectAttribute("content");
							writer.openObject();
							writer.addObjectAttribute("application/json");
							writer.openObject();
							writer.addObjectAttribute("schema");
							writer.openObject();
							new JSONSpecWriter(writer).specifyValue(null, op.bodyType, provider.getParent().getSerializationRules()).blockThrow(0);
							writer.closeObject(); // schema
							writer.closeObject(); // json
							writer.closeObject(); // content
							writer.addObjectAttribute("required", op.bodyRequired);
							writer.closeObject(); // requestBody
						}
						writer.addObjectAttribute("responses");
						writer.openObject();
						writer.addObjectAttribute("400");
						writer.openObject();
						writer.addObjectAttribute("description", "Bad request or invalid input");
						writer.closeObject();
						if (op.needsAuthentication) {
							writer.addObjectAttribute("403");
							writer.openObject();
							writer.addObjectAttribute("description", "Bad request or invalid input");
							writer.closeObject();
						}
						writer.addObjectAttribute("200");
						writer.openObject();
						writer.addObjectAttribute("description", "Successful operation");
						if (op.returnType != null) {
							writer.addObjectAttribute("content");
							writer.openObject();
							writer.addObjectAttribute("application/json");
							writer.openObject();
							writer.addObjectAttribute("schema");
							writer.openObject();
							new JSONSpecWriter(writer).specifyValue(null, op.returnType, provider.getParent().getSerializationRules()).blockThrow(0);
							writer.closeObject(); // schema
							writer.closeObject(); // json
							writer.closeObject(); // content
						}
						writer.closeObject(); // 200
						writer.closeObject(); // responses
						writer.closeObject(); // HTTP method
					}
					writer.closeObject();
				}
				writer.closeObject();

				writer.closeObject();
				writer.flush().listenInlineSP(result);
			} catch (Exception e) {
				result.error(e);
			} catch (Throwable t) {
				result.error(new Exception(t));
			}
		}).start();
		return result;
	}
	
}
