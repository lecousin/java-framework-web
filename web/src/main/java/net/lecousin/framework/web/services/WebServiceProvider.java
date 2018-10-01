package net.lecousin.framework.web.services;

import java.util.List;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestProcessor;
import net.lecousin.framework.web.WebResourcesBundle;

/** Provide a specific type of web service, such as REST, or SOAP... */
public abstract class WebServiceProvider<WebServiceType extends WebService> implements WebRequestProcessor {

	protected WebServiceProvider(WebResourcesBundle bundle, WebServiceType service) {
		this.bundle = bundle;
		this.service = service;
	}
	
	protected WebResourcesBundle bundle;
	protected WebServiceType service;
	
	@Override
	public final WebResourcesBundle getParent() {
		return bundle;
	}
	
	@Override
	public final void setParent(WebRequestProcessor parent) {
		throw new IllegalStateException("setParent cannot be called on a web service provider");
	}
	
	/** Type of web service, for documentation purpose. */
	public abstract String getServiceTypeName();
	
	/** Default path of the service, if not specified in the configuration. */
	public abstract String getDefaultPath();
	
	/** Web service instance. */
	public final WebServiceType getWebService() {
		return service;
	}
	
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
	public abstract List<OperationDescription> getOperations();
	
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
	public abstract List<WebServiceSpecification> getSpecifications();
	
	@Override
	public final ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		request.getResponse().addHeaderRaw("X-WebService", getServiceTypeName());
		return processServiceRequest(fromCheck, request);
	}

	protected abstract ISynchronizationPoint<? extends Exception> processServiceRequest(Object fromCheck, WebRequest request);
	
}
