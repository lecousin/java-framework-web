package net.lecousin.framework.web.services;

import net.lecousin.framework.plugins.Plugin;
import net.lecousin.framework.web.WebResourcesBundle;

public interface WebServiceProviderPlugin extends Plugin {

	boolean supportService(Class<?> serviceClass);
	
	WebServiceProvider createProvider(WebResourcesBundle bundle, Object service) throws Exception;
	
}
