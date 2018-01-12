package net.lecousin.framework.web.services;

import java.util.List;

import net.lecousin.framework.web.WebRequestProcessor;
import net.lecousin.framework.web.WebResourcesBundle;

/** Provide a specific type of web service, such as REST, or SOAP... */
public interface WebServiceProvider extends WebRequestProcessor {

	@Override
	public WebResourcesBundle getParent();
	
	/** Type of web service, for documentation purpose. */
	public String getServiceTypeName();
	
	/** Default path of the service, if not specified in the configuration. */
	public String getDefaultPath();
	
	/** Web service instance. */
	public Object getWebService();
	
	/** Describe an operation, for documentation. */
	public static class OperationDescription {
		/** Constructor. */
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
		/** Constructor. */
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
