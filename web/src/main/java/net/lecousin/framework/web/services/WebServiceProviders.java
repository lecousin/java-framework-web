package net.lecousin.framework.web.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.lecousin.framework.plugins.ExtensionPoint;

/** Extension point to declare web service providers. */
public class WebServiceProviders implements ExtensionPoint<WebServiceProviderPlugin> {

	private ArrayList<WebServiceProviderPlugin> plugins = new ArrayList<>();
	
	@Override
	public Class<WebServiceProviderPlugin> getPluginClass() {
		return WebServiceProviderPlugin.class;
	}

	@Override
	public void addPlugin(WebServiceProviderPlugin plugin) {
		synchronized (plugins) {
			plugins.add(plugin);
		}
	}

	@Override
	public void allPluginsLoaded() {
	}

	@Override
	public Collection<WebServiceProviderPlugin> getPlugins() {
		return plugins;
	}
	
	/** Return the list of plugins that are capable to provide services from the given class.
	 * It returns a list because a class may provide different kind of services.
	 */
	public List<WebServiceProviderPlugin> getPluginsFor(Class<?> cl) {
		List<WebServiceProviderPlugin> list = new ArrayList<>();
		for (WebServiceProviderPlugin plugin : plugins)
			if (plugin.supportService(cl))
				list.add(plugin);
		return list;
	}

}
