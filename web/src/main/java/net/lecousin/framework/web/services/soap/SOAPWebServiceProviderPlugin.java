package net.lecousin.framework.web.services.soap;

import net.lecousin.framework.web.WebResourcesBundle;
import net.lecousin.framework.web.services.WebServiceProvider;
import net.lecousin.framework.web.services.WebServiceProviderPlugin;

public class SOAPWebServiceProviderPlugin implements WebServiceProviderPlugin {

	@Override
	public boolean supportService(Class<?> serviceClass) {
		return SOAP.class.isAssignableFrom(serviceClass);
	}
	
	@Override
	public WebServiceProvider createProvider(WebResourcesBundle bundle, Object service) throws Exception {
		return new SOAPWebServiceProvider(bundle, (SOAP)service);
	}
	
}
