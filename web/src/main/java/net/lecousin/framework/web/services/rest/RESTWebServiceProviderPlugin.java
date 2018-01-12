package net.lecousin.framework.web.services.rest;

import net.lecousin.framework.web.WebResourcesBundle;
import net.lecousin.framework.web.services.WebServiceProvider;
import net.lecousin.framework.web.services.WebServiceProviderPlugin;

public class RESTWebServiceProviderPlugin implements WebServiceProviderPlugin {

	@Override
	public boolean supportService(Class<?> serviceClass) {
		return REST.class.isAssignableFrom(serviceClass);
	}
	
	@Override
	public WebServiceProvider createProvider(WebResourcesBundle bundle, Object service) throws Exception {
		return new RESTWebServiceProvider(bundle, (REST)service);
	}
	
}
