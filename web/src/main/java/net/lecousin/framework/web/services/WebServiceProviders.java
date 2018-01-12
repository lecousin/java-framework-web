package net.lecousin.framework.web.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.lecousin.framework.plugins.ExtensionPoint;

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
	
	public List<WebServiceProviderPlugin> getPluginsFor(Class<?> cl) {
		List<WebServiceProviderPlugin> list = new ArrayList<>();
		for (WebServiceProviderPlugin plugin : plugins)
			if (plugin.supportService(cl))
				list.add(plugin);
		return list;
	}

}
