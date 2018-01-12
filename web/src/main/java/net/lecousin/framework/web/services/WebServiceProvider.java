package net.lecousin.framework.web.services;

import java.util.List;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebResourcesBundle;

/** Provide a specific type of web service, such as REST, or SOAP... */
public interface WebServiceProvider {

	public String getServiceTypeName();
	
	public String getDefaultPath();
	public Object getWebService();
	
	public Object checkProcessing(WebRequest request);
	
	public ISynchronizationPoint<?> process(Object fromCheck, WebRequest request, WebResourcesBundle bundle);
	
	/** Describe an operation, for documentation. */
	public static class OperationDescription {
		public OperationDescription(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		protected String name;
		protected String description;
		
		public String getName() { return name; }

		public String getDescription() { return description; }
	}
	
	/** Get a list of available operations, for documentation. */
	public List<OperationDescription> getOperations();
	
	/** Describe a specification of the service. */
	public static class WebServiceSpecification {
		public WebServiceSpecification(String name, String path) {
			this.name = name;
			this.path = path;
		}
		
		private String name;
		private String path;
		
		public String getName() { return name; }

		public String getPath() { return path; }
	}
	
	/** Get a list of available specifications. */
	public List<WebServiceSpecification> getSpecifications();
	
}
