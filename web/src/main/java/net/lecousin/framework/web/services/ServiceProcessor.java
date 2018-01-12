package net.lecousin.framework.web.services;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.serialization.annotations.Transient;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestProcessor;
import net.lecousin.framework.web.WebResourcesBundle;

/** Processor for web services. */
public class ServiceProcessor implements WebRequestProcessor {

	public ServiceProcessor(WebResourcesBundle bundle, WebServiceProvider provider) {
		this.bundle = bundle;
		this.provider = provider;
	}
	
	@Transient
	private WebResourcesBundle bundle;
	@Transient
	private WebServiceProvider provider;
	
	@Override
	public WebRequestProcessor getParent() {
		return bundle;
	}
	
	@Override
	public void setParent(WebRequestProcessor parent) {
		throw new IllegalStateException();
	}
	
	public WebServiceProvider getServiceProvider() {
		return provider;
	}

	@Override
	public Object checkProcessing(WebRequest request) {
		return provider.checkProcessing(request);
	}
	
	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		return provider.process(fromCheck, request, bundle);
	}
	
}
