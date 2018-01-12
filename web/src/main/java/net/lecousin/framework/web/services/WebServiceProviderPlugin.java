package net.lecousin.framework.web.services;

import net.lecousin.framework.plugins.Plugin;
import net.lecousin.framework.web.WebResourcesBundle;

/** Plugin to declare and instantiate a WebServiceProvider. */
public interface WebServiceProviderPlugin extends Plugin {

	/** Return true if the WebServiceProvider is capable of providing services from the given class. */
	boolean supportService(Class<?> serviceClass);
	
	/** Instantiate a WebServiceProvider. */
	WebServiceProvider createProvider(WebResourcesBundle bundle, Object service) throws Exception;
	
}
