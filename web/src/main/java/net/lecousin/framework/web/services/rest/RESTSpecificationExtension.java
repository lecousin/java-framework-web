package net.lecousin.framework.web.services.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.lecousin.framework.plugins.ExtensionPoint;

public class RESTSpecificationExtension implements ExtensionPoint<RESTSpecificationPlugin> {

	private List<RESTSpecificationPlugin> plugins = new ArrayList<>(2);
	
	@Override
	public Class<RESTSpecificationPlugin> getPluginClass() {
		return RESTSpecificationPlugin.class;
	}

	@Override
	public void addPlugin(RESTSpecificationPlugin plugin) {
		synchronized (plugins) {
			plugins.add(plugin);
		}
	}

	@Override
	public void allPluginsLoaded() {
	}

	@Override
	public Collection<RESTSpecificationPlugin> getPlugins() {
		return plugins;
	}

}
