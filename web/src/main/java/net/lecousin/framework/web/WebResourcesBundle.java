package net.lecousin.framework.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.Injection;
import net.lecousin.framework.injection.InjectionContext;
import net.lecousin.framework.injection.InjectionXmlConfiguration;
import net.lecousin.framework.injection.ObjectAttribute;
import net.lecousin.framework.injection.ObjectMethod;
import net.lecousin.framework.injection.Singleton;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.serialization.Deserializer;
import net.lecousin.framework.io.serialization.Serializer;
import net.lecousin.framework.io.serialization.annotations.Transient;
import net.lecousin.framework.io.serialization.rules.SerializationRule;
import net.lecousin.framework.json.JSONDeserializer;
import net.lecousin.framework.json.JSONSerializer;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.websocket.WebSocketDispatcher.WebSocketHandler;
import net.lecousin.framework.network.http.websocket.WebSocketDispatcher.WebSocketRouter;
import net.lecousin.framework.network.server.TCPServerClient;
import net.lecousin.framework.plugins.ExtensionPoints;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Provider;
import net.lecousin.framework.util.Triple;
import net.lecousin.framework.util.UnprotectedStringBuffer;
import net.lecousin.framework.web.services.WebServiceProvider;
import net.lecousin.framework.web.services.WebServiceProviderPlugin;
import net.lecousin.framework.web.services.WebServiceProviders;
import net.lecousin.framework.xml.XMLStreamEvents.ElementContext;
import net.lecousin.framework.xml.XMLStreamReader;
import net.lecousin.framework.xml.serialization.XMLDeserializer;
import net.lecousin.framework.xml.serialization.XMLSerializer;

public class WebResourcesBundle implements WebRequestProcessor {
	
	// TODO customize mime types ?
	// TODO serialization configuration

	public WebResourcesBundle() {
		this(null);
	}
	
	public WebResourcesBundle(InjectionContext injection) {
		this.injection = injection;
		deserializersByMimeType.put("text/xml", xmlDeserializerFactory);
		serializersByMimeType.put("text/xml", xmlSerializerFactory);
		deserializersByMimeType.put("application/json", jsonDeserializerFactory);
		serializersByMimeType.put("application/json", jsonSerializerFactory);
		deserializersByMimeType.put("text/json", jsonDeserializerFactory);
		serializersByMimeType.put("text/json", jsonSerializerFactory);
	}
	
	public static final Provider.FromValue<Pair<Class<?>, Charset>, Deserializer> xmlDeserializerFactory = new Provider.FromValue<Pair<Class<?>, Charset>, Deserializer>() {
		@Override
		public Deserializer provide(Pair<Class<?>, Charset> p) {
			return new XMLDeserializer(null, p.getValue1().getSimpleName(), p.getValue2());
		}
	};
	public static final Provider.FromValue<Pair<Class<?>, Charset>, Serializer> xmlSerializerFactory = new Provider.FromValue<Pair<Class<?>, Charset>, Serializer>() {
		@Override
		public Serializer provide(Pair<Class<?>, Charset> p) {
			return new XMLSerializer(null, p.getValue1().getSimpleName(), null, p.getValue2(), 4096, true);
		}
	};
	public static final Provider.FromValue<Pair<Class<?>, Charset>, Deserializer> jsonDeserializerFactory = new Provider.FromValue<Pair<Class<?>, Charset>, Deserializer>() {
		@Override
		public Deserializer provide(Pair<Class<?>, Charset> p) {
			return new JSONDeserializer(p.getValue2());
		}
	};
	public static final Provider.FromValue<Pair<Class<?>, Charset>, Serializer> jsonSerializerFactory = new Provider.FromValue<Pair<Class<?>, Charset>, Serializer>() {
		@Override
		public Serializer provide(Pair<Class<?>, Charset> p) {
			return new JSONSerializer(p.getValue2(), 4096, false);
		}
	};
	
	@Transient
	private WebRequestProcessor parent = null;
	/** Pre-processing. */
	@Transient
	private List<WebRequestFilter> preProcessing = new ArrayList<>();
	/** Association between a sub-path, and a processor. A sub-path must end with a / or be empty. */
	@Transient
	private List<Pair<String, WebRequestProcessor>> processors = new ArrayList<>();
	/** Post-processing. */
	@Transient
	private List<WebRequestFilter> postProcessing = new ArrayList<>();
	/** Web-sockets. */
	@Transient
	private List<Pair<String, WebSocketRouter>> wsRouters = new ArrayList<>();
	/** Custom filters. */
	@Transient
	private List<Object> customFilters = new ArrayList<>();
	/** Injection. */
	@Transient
	private InjectionContext injection = null;
	/** Serialization configuration. */
	@Transient
	private Map<String, Provider.FromValue<Pair<Class<?>, Charset>, Deserializer>> deserializersByMimeType = new HashMap<>();
	@Transient
	private Map<String, Provider.FromValue<Pair<Class<?>, Charset>, Serializer>> serializersByMimeType = new HashMap<>();
	@Transient
	private List<SerializationRule> serializationRules = new ArrayList<>();
	
	@Override
	public WebRequestProcessor getParent() {
		return parent;
	}
	
	@Override
	public void setParent(WebRequestProcessor parent) {
		this.parent = parent;
		if (injection != null)
			injection.setParent(parent.getInjectionContext());
	}
	
	@Override
	public InjectionContext getInjectionContext() {
		if (parent == null && injection == null)
			injection = new InjectionContext();
		if (injection != null)
			return injection;
		return parent.getInjectionContext();
	}
	
	public void addPreProcessor(WebRequestFilter filter) {
		preProcessing.add(filter);
	}

	public void addPostProcessor(WebRequestFilter filter) {
		postProcessing.add(filter);
	}
	
	public void addProcessor(String path, WebRequestProcessor processor) {
		processor.setParent(this);
		processors.add(new Pair<>(path, processor));
	}
	
	public List<Pair<String, WebRequestProcessor>> getProcessors() {
		return processors;
	}
	
	public Deserializer getDeserializer(WebRequest request, Class<?> type) {
		Pair<String, Map<String, String>> p;
		try { p = request.getRequest().getMIME().parseContentType(); }
		catch (IOException e) {
			return null;
		}
		if (p == null)
			return null;
		String encoding = p.getValue2().get("charset");
		Charset charset = null;
		if (encoding != null)
			try { charset = Charset.forName(encoding); }
			catch (Throwable t) { /* ignore */ }
		return getDeserializer(p.getValue1(), charset, type);
	}
	
	public Deserializer getDeserializer(String contentType, Charset encoding, Class<?> type) {
		Provider.FromValue<Pair<Class<?>, Charset>, Deserializer> provider = deserializersByMimeType.get(contentType);
		if (provider == null) {
			if (parent instanceof WebResourcesBundle)
				return ((WebResourcesBundle)parent).getDeserializer(contentType, encoding, type);
			return null;
		}
		return provider.provide(new Pair<>(type, encoding));
	}

	public Serializer getSerializer(String contentType, Charset encoding, Class<?> type) {
		Provider.FromValue<Pair<Class<?>, Charset>, Serializer> provider = serializersByMimeType.get(contentType);
		if (provider == null) {
			if (parent instanceof WebResourcesBundle)
				return ((WebResourcesBundle)parent).getSerializer(contentType, encoding, type);
			return null;
		}
		return provider.provide(new Pair<>(type, encoding));
	}
	
	public List<SerializationRule> getSerializationRules() {
		return serializationRules; // TODO get from parent ?
	}
	
	@SuppressWarnings("unchecked")
	public <T> List<T> getCustomFilters(Class<T> type) {
		 // TODO get from parent
		LinkedList<T> list = new LinkedList<>();
		for (Object filter : customFilters)
			if (type.isAssignableFrom(filter.getClass()))
				list.add((T)filter);
		return list;
	}
	
	@Override
	public Object checkProcessing(WebRequest request) {
		List<Pair<String, WebRequestProcessor>> list = getProcessors(request.getSubPath());
		if (list.isEmpty()) return null;
		String myPath = request.getCurrentPath();
		String subPath = request.getSubPath();
		for (Pair<String, WebRequestProcessor> p : list) {
			request.setPath(myPath + p.getValue1(), subPath.length() > p.getValue1().length() ? subPath.substring(p.getValue1().length()) : "");
			Object o = p.getValue2().checkProcessing(request);
			if (o != null) {
				request.setPath(myPath, subPath);
				return new Triple<String, WebRequestProcessor, Object>(p.getValue1(), p.getValue2(), o);
			}
		}
		request.setPath(myPath, subPath);
		return null;
	}
	
	@Override
	public ISynchronizationPoint<Exception> process(Object fromCheck, WebRequest request) {
		@SuppressWarnings("unchecked")
		Triple<String, WebRequestProcessor, Object> t = (Triple<String, WebRequestProcessor, Object>)fromCheck;
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		preProcess(0, request, t.getValue2(), t.getValue1(), t.getValue3(), sp);
		return sp;
	}
	
	private void preProcess(
		int preProcessorIndex, WebRequest request,
		WebRequestProcessor processor, String processorPath, Object processorCheck,
		SynchronizationPoint<Exception> sp
	) {
		if (sp.isCancelled()) return;
		if (preProcessorIndex == preProcessing.size()) {
			// end of pre-processing
			String myPath = request.getCurrentPath();
			String subPath = request.getSubPath();
			request.setPath(myPath + processorPath, subPath.length() > processorPath.length() ? subPath.substring(processorPath.length()) : "");
			ISynchronizationPoint<? extends Exception> process = processor.process(processorCheck, request);
			sp.onCancel((reason) -> { process.cancel(reason); });
			process.listenInline(() -> {
				request.setPath(myPath, subPath);
				if (process.hasError()) {
					sp.error(process.getError());
					return;
				}
				if (process.isCancelled()) {
					sp.cancel(process.getCancelEvent());
					return;
				}
				new Task.Cpu<Void, NoException>("Post-processing of Web request", Task.PRIORITY_NORMAL) {
					@Override
					public Void run() {
						postProcess(0, request, sp);
						return null;
					}
				}.start();
			});
			return;
		}
		WebRequestFilter filter = preProcessing.get(preProcessorIndex);
		filter.filter(request).listenInline(
			(result) -> {
				if (sp.isCancelled()) return;
				new Task.Cpu<Void, NoException>("Post-processing of Web request", Task.PRIORITY_NORMAL) {
					@Override
					public Void run() {
						if (sp.isCancelled()) return null;
						switch (result) {
						default:
						case CONTINUE_PROCESSING:
							preProcess(preProcessorIndex + 1, request, processor, processorPath, processorCheck, sp);
							break;
						case STOP_PROCESSING:
							postProcess(0, request, sp);
							break;
						case RESTART_PROCESSING:
							WebRequestProcessor root = parent;
							do {
								WebRequestProcessor p = root.getParent();
								if (p != null) root = p;
								else break;
							} while (true);
							Object fromCheck = root.checkProcessing(request);
							ISynchronizationPoint<? extends Exception> p = root.process(fromCheck, request);
							sp.onCancel((reason) -> { p.cancel(reason); });
							p.listenInlineSP(sp);
							break;
						}
						return null;
					}
				}.start();
			},
			sp
		);
	}
	
	private void postProcess(int postProcessorIndex, WebRequest request, SynchronizationPoint<Exception> sp) {
		if (postProcessorIndex == postProcessing.size()) {
			// end of post-processing
			sp.unblock();
			return;
		}
		if (sp.isCancelled()) return;
		WebRequestFilter filter = postProcessing.get(postProcessorIndex);
		filter.filter(request).listenInline(
			(result) -> {
				if (sp.isCancelled()) return;
				new Task.Cpu<Void, NoException>("Post-processing of Web request", Task.PRIORITY_NORMAL) {
					@Override
					public Void run() {
						if (sp.isCancelled()) return null;
						switch (result) {
						default:
						case CONTINUE_PROCESSING:
						case STOP_PROCESSING:
							postProcess(postProcessorIndex + 1, request, sp);
							break;
						case RESTART_PROCESSING:
							WebRequestProcessor root = parent;
							do {
								WebRequestProcessor p = root.getParent();
								if (p != null) root = p;
								else break;
							} while (true);
							Object fromCheck = root.checkProcessing(request);
							ISynchronizationPoint<? extends Exception> p = root.process(fromCheck, request);
							sp.onCancel((reason) -> { p.cancel(reason); });
							p.listenInlineSP(sp);
							break;
						}
						return null;
					}
				}.start();
			},
			sp
		);
	}
	
	@Override
	public WebSocketHandler getWebSocketHandler(TCPServerClient client, HTTPRequest request, String path, String[] protocols) {
		for (Pair<String, WebSocketRouter> p : wsRouters) {
			if (path.startsWith(p.getValue1())) {
				String subPath = path.substring(p.getValue1().length());
				WebSocketHandler handler = p.getValue2().getWebSocketHandler(client, request, subPath, protocols);
				if (handler != null)
					return handler;
			} else if (p.getValue1().length() > 0 && path.equals(p.getValue1().substring(0, p.getValue1().length() - 1))) {
				WebSocketHandler handler = p.getValue2().getWebSocketHandler(client, request, "", protocols);
				if (handler != null)
					return handler;
			}
		}
		
		List<Pair<String, WebRequestProcessor>> list = getProcessors(path);
		if (list.isEmpty()) return null;
		for (Pair<String, WebRequestProcessor> p : list) {
			if (path.startsWith(p.getValue1())) {
				String subPath = path.substring(p.getValue1().length());
				WebSocketHandler handler = p.getValue2().getWebSocketHandler(client, request, subPath, protocols);
				if (handler != null)
					return handler;
			} else if (p.getValue1().length() > 0 && path.equals(p.getValue1().substring(0, p.getValue1().length() - 1))) {
				WebSocketHandler handler = p.getValue2().getWebSocketHandler(client, request, "", protocols);
				if (handler != null)
					return handler;
			}
		}
		return null;
	}
	
	private static final Comparator<Pair<String, WebRequestProcessor>> processorComparator =
		new Comparator<Pair<String, WebRequestProcessor>>() {
			@Override
			public int compare(Pair<String, WebRequestProcessor> p1, Pair<String, WebRequestProcessor> p2) {
				return p2.getValue1().length() - p1.getValue1().length();
			}
		};
		
	private List<Pair<String, WebRequestProcessor>> getProcessors(String subPath) {
		LinkedList<Pair<String, WebRequestProcessor>> list = new LinkedList<>();
		for (Pair<String, WebRequestProcessor> p : processors)
			if (p.getValue1().isEmpty() ||
				subPath.equals(p.getValue1().substring(0, p.getValue1().length() - 1)) ||
				subPath.startsWith(p.getValue1()))
				list.add(p);
		if (list.size() > 1)
			Collections.sort(list, processorComparator);
		return list;
	}
	
	@Override
	public void configure(String filename) throws Exception {
		@SuppressWarnings("resource")
		IO.Readable io = LCCore.getApplication().getResource(filename, Task.PRIORITY_NORMAL);
		if (io == null) {
			File f = new File(filename);
			if (!f.exists())
				throw new FileNotFoundException(filename);
			io = new FileIO.ReadOnly(f, Task.PRIORITY_NORMAL);
		}
		try {
			XMLStreamReader xml = new XMLStreamReader(io, 4096);
			xml.startRootElement();
			configure(xml);
		} finally {
			io.closeAsync();
		}
	}
	
	/** Configure from a bundle XML element. */
	public void configure(XMLStreamReader xml) throws Exception {
		Application app = LCCore.getApplication();
		UnprotectedStringBuffer configPath = xml.getAttributeValueByLocalName("config");
		if (configPath != null) {
			configure(Injection.resolveProperties(getInjectionContext(), app, configPath.asString()));
		}
		ElementContext elem = xml.event.context.getFirst();
		if (!xml.nextInnerElement(elem)) return;
		while (xml.event.text.equals("Injection")) {
			configureInjection(xml);
			if (!xml.nextInnerElement(elem)) return;
		}
		while (xml.event.text.equals("custom-filter")) {
			customFilters.add(configureCustomFilter(xml, app));
			if (!xml.nextInnerElement(elem)) return;
		}
		while (xml.event.text.equals("pre-filter")) {
			preProcessing.add(configureFilter(xml, app));
			if (!xml.nextInnerElement(elem)) return;
		}
		do {
			if (xml.event.text.equals("processor") || xml.event.text.equals("bundle")) {
				processors.add(configureProcessor(xml, app));
				if (!xml.nextInnerElement(elem)) return;
			} else if (xml.event.text.equals("static")) {
				processors.add(configureStaticProcessor(xml, app));
				if (!xml.nextInnerElement(elem)) return;
			} else if (xml.event.text.equals("service")) {
				configureService(xml, app);
				if (!xml.nextInnerElement(elem)) return;
			} else
				break;
		} while (true);
		while (xml.event.text.equals("post-filter")) {
			postProcessing.add(configureFilter(xml, app));
			if (!xml.nextInnerElement(elem)) return;
		}
		do {
			if (xml.event.text.equals("web-socket-router")) {
				wsRouters.add(configureWebSocketRouter(xml, app));
			} else if (xml.event.text.equals("configurator")) {
				callConfigurator(xml, app);
			} else
				throw new Exception("Unexpected element " + xml.event.text.asString() + " in a bundle");
		} while (xml.nextInnerElement(elem));
	}
	
	private void configureInjection(XMLStreamReader xml) throws Exception {
		if (injection == null)
			injection = new InjectionContext(parent != null ? parent.getInjectionContext() : null);
		InjectionXmlConfiguration.configureInjection(injection, xml);
	}
	
	private WebRequestFilter configureFilter(XMLStreamReader xml, Application app) throws Exception {
		String className = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("class"));
		if (className == null)
			throw new Exception("Missing attribute class on element filter");
		Class<?> cl = app.getClassLoader().loadClass(className);
		if (!WebRequestFilter.class.isAssignableFrom(cl))
			throw new Exception("Invalid filter class, it must implement WebRequestFilter: " + className);
		String id = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("id"));
		List<ObjectAttribute> attrs = new LinkedList<>();
		ObjectMethod initMethod = null;
		if (!xml.event.isClosed) {
			ElementContext elem = xml.event.context.getFirst();
			while (xml.nextInnerElement(elem)) {
				if (xml.event.text.equals("attribute"))
					attrs.add(InjectionXmlConfiguration.readObjectAttribute01(getInjectionContext(), xml, app));
				else if (xml.event.text.equals("init-method"))
					initMethod = InjectionXmlConfiguration.readObjectMethod01(xml);
				else
					throw new Exception("Unexpected element " + xml.event.text.asString() + " in filter");
			}
		}
		WebRequestFilter filter = (WebRequestFilter)Injection.create(getInjectionContext(), cl, initMethod, attrs);
		if (id != null) getInjectionContext().add(new Singleton(cl, filter, id));
		return filter;
	}
	
	private Object configureCustomFilter(XMLStreamReader xml, Application app) throws Exception {
		String className = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("class"));
		if (className == null)
			throw new Exception("Missing attribute class on element custom-filter");
		Class<?> cl = app.getClassLoader().loadClass(className);
		String id = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("id"));
		List<ObjectAttribute> attrs = new LinkedList<>();
		ObjectMethod initMethod = null;
		if (!xml.event.isClosed) {
			ElementContext elem = xml.event.context.getFirst();
			while (xml.nextInnerElement(elem)) {
				if (xml.event.text.equals("attribute"))
					attrs.add(InjectionXmlConfiguration.readObjectAttribute01(getInjectionContext(), xml, app));
				else if (xml.event.text.equals("init-method"))
					initMethod = InjectionXmlConfiguration.readObjectMethod01(xml);
				else
					throw new Exception("Unexpected element " + xml.event.text.asString() + " in filter");
			}
		}
		Object filter = Injection.create(getInjectionContext(), cl, initMethod, attrs);
		if (id != null) getInjectionContext().add(new Singleton(cl, filter, id));
		return filter;
	}
	
	private Pair<String, WebRequestProcessor> configureProcessor(XMLStreamReader xml, Application app) throws Exception {
		String path = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("path"));
		if (path == null)
			throw new Exception("Missing path attribute on element " + xml.event.text.asString());
		if (!path.isEmpty() && !path.endsWith("/"))
			path += "/";
		String id = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("id"));
		if (xml.event.text.equals("bundle")) {
			WebResourcesBundle bundle = new WebResourcesBundle();
			bundle.setParent(this);
			bundle.configure(xml);
			if (id != null) getInjectionContext().add(new Singleton(WebResourcesBundle.class, bundle, id));
			return new Pair<>(path, bundle);
		}
		String className = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("class"));
		if (className == null)
			throw new Exception("Missing attribute class on element " + xml.event.text.asString());
		Class<?> cl = app.getClassLoader().loadClass(className);
		if (!WebRequestProcessor.class.isAssignableFrom(cl))
			throw new Exception("Invalid processor class, it must implement WebRequestProcessor: " + className);
		String config = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("config"));
		List<ObjectAttribute> attrs = new LinkedList<>();
		ObjectMethod initMethod = null;
		if (!xml.event.isClosed) {
			ElementContext elem = xml.event.context.getFirst();
			while (xml.nextInnerElement(elem)) {
				if (xml.event.text.equals("attribute"))
					attrs.add(InjectionXmlConfiguration.readObjectAttribute01(getInjectionContext(), xml, app));
				else if (xml.event.text.equals("init-method"))
					initMethod = InjectionXmlConfiguration.readObjectMethod01(xml);
				else
					throw new Exception("Unexpected element " + xml.event.text.asString() + " in processor");
			}
		}
		WebRequestProcessor processor = (WebRequestProcessor)Injection.create(getInjectionContext(), cl, initMethod, attrs);
		processor.setParent(this);
		if (id != null) getInjectionContext().add(new Singleton(cl, processor, id));
		if (config != null)
			processor.configure(config);
		return new Pair<>(path, processor);
	}

	private Pair<String, WebRequestProcessor> configureStaticProcessor(XMLStreamReader xml, Application app) throws Exception {
		String path = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("path"));
		if (path == null)
			throw new Exception("Missing path attribute on element " + xml.event.text.asString());
		if (!path.isEmpty() && !path.endsWith("/"))
			path += "/";
		List<ObjectAttribute> attrs = new LinkedList<>();
		if (!xml.event.isClosed) {
			ElementContext elem = xml.event.context.getFirst();
			while (xml.nextInnerElement(elem)) {
				if (xml.event.text.equals("attribute"))
					attrs.add(InjectionXmlConfiguration.readObjectAttribute01(getInjectionContext(), xml, app));
				else
					throw new Exception("Unexpected element " + xml.event.text.asString() + " in processor");
			}
		}
		WebRequestProcessor processor = (WebRequestProcessor)Injection.create(getInjectionContext(), StaticResourcesProcessor.class, null, attrs);
		return new Pair<>(path, processor);
	}
	
	private void configureService(XMLStreamReader xml, Application app) throws Exception {
		String path = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("path"));
		String className = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("class"));
		if (className == null)
			throw new Exception("Missing attribute class on element service");
		Class<?> cl = app.getClassLoader().loadClass(className);
		
		List<WebServiceProviderPlugin> plugins = ExtensionPoints.getExtensionPoint(WebServiceProviders.class).getPluginsFor(cl);
		if (plugins.isEmpty())
			throw new Exception("No WebServiceProvider available for web service class " + className);
		
		Object service = cl.newInstance();
		Injection.inject(getInjectionContext(), service);
		
		String id = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("id"));
		if (id != null) getInjectionContext().add(new Singleton(cl, service, id));

		for (WebServiceProviderPlugin plugin : plugins) {
			WebServiceProvider provider = plugin.createProvider(this, service);
			Injection.inject(getInjectionContext(), provider);
	
			if (path == null)
				path = provider.getDefaultPath();
			if (!path.isEmpty() && !path.endsWith("/"))
				path += "/";
			processors.add(new Pair<>(path, provider));
		}
	}
	
	private Pair<String, WebSocketRouter> configureWebSocketRouter(XMLStreamReader xml, Application app) throws Exception {
		String path = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("path"));
		if (path == null) path = "";
		if (path.length() > 0 && !path.endsWith("/")) path += "/";
		String className = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("class"));
		if (className == null)
			throw new Exception("Missing attribute class on element " + xml.event.text.asString());
		Class<?> cl = app.getClassLoader().loadClass(className);
		if (!WebRequestRouter.class.isAssignableFrom(cl))
			throw new Exception("Invalid WebSocketRouter class: " + className);
		String id = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("id"));
		List<ObjectAttribute> attrs = new LinkedList<>();
		ObjectMethod initMethod = null;
		if (!xml.event.isClosed) {
			ElementContext elem = xml.event.context.getFirst();
			while (xml.nextInnerElement(elem)) {
				if (xml.event.text.equals("attribute"))
					attrs.add(InjectionXmlConfiguration.readObjectAttribute01(getInjectionContext(), xml, app));
				else if (xml.event.text.equals("init-method"))
					initMethod = InjectionXmlConfiguration.readObjectMethod01(xml);
				else
					throw new Exception("Unexpected element " + xml.event.text.asString() + " in web-socket-router");
			}
		}
		WebSocketRouter router = (WebSocketRouter)Injection.create(getInjectionContext(), cl, initMethod, attrs);
		if (id != null) getInjectionContext().add(new Singleton(cl, router, id));
		return new Pair<>(path, router);
	}

	private void callConfigurator(XMLStreamReader xml, Application app) throws Exception {
		String className = Injection.resolveProperties(getInjectionContext(), app, xml.getAttributeValueByLocalName("class"));
		if (className == null)
			throw new Exception("Missing attribute class on element " + xml.event.text.asString());
		Class<?> cl = app.getClassLoader().loadClass(className);
		if (!WebResourcesBundleConfigurator.class.isAssignableFrom(cl))
			throw new Exception("Invalid WebResourcesBundleConfigurator class: " + className);
		List<ObjectAttribute> attrs = new LinkedList<>();
		ObjectMethod initMethod = null;
		if (!xml.event.isClosed) {
			ElementContext elem = xml.event.context.getFirst();
			while (xml.nextInnerElement(elem)) {
				if (xml.event.text.equals("attribute"))
					attrs.add(InjectionXmlConfiguration.readObjectAttribute01(getInjectionContext(), xml, app));
				else if (xml.event.text.equals("init-method"))
					initMethod = InjectionXmlConfiguration.readObjectMethod01(xml);
				else
					throw new Exception("Unexpected element " + xml.event.text.asString() + " in configurator");
			}
		}
		WebResourcesBundleConfigurator configurator = (WebResourcesBundleConfigurator)Injection.create(getInjectionContext(), cl, initMethod, attrs);
		configurator.configure(this);
	}
	
}
