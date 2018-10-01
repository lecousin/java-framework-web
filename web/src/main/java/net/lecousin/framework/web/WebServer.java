package net.lecousin.framework.web;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLContext;

import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.InjectionContext;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.network.NetUtil;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.server.HTTPRequestProcessor;
import net.lecousin.framework.network.http.server.HTTPServerProtocol;
import net.lecousin.framework.network.http.server.HTTPServerResponse;
import net.lecousin.framework.network.http.websocket.WebSocketDispatcher;
import net.lecousin.framework.network.http.websocket.WebSocketDispatcher.WebSocketHandler;
import net.lecousin.framework.network.http.websocket.WebSocketServerProtocol;
import net.lecousin.framework.network.server.TCPServer;
import net.lecousin.framework.network.server.TCPServerClient;
import net.lecousin.framework.network.server.protocol.SSLServerProtocol;
import net.lecousin.framework.network.server.protocol.ServerProtocol;
import net.lecousin.framework.network.session.ISession;
import net.lecousin.framework.network.session.NetworkSessionProvider;
import net.lecousin.framework.network.session.Session;
import net.lecousin.framework.network.session.SessionStorage;
import net.lecousin.framework.network.ssl.SSLContextConfig;

public class WebServer implements Closeable {

	public WebServer() {
		this(null, null, true);
	}
	
	public WebServer(
		SSLContext sslContext,
		SessionStorage sessionStorage, boolean sessionOnlyOnSecureChannel
	) {
		this.ssl = sslContext;
		if (sessionStorage != null)
			sessionProvider = new NetworkSessionProvider(sessionStorage, "WebServer");
		this.sessionOnlyOnSecureChannel = sessionOnlyOnSecureChannel;
		app = LCCore.getApplication();
		logger = app.getLoggerFactory().getLogger(WebServer.class);
		injection = new InjectionContext(app.getInstance(InjectionContext.class));
		app.toClose(this);
		httpProtocol = new HTTPServerProtocol(httpProcessor);
		WebSocketDispatcher wsDispatcher = new WebSocketDispatcher(root);
		WebSocketServerProtocol wsProtocol = new WebSocketServerProtocol(wsDispatcher);
		httpProtocol.enableWebSocket(wsProtocol);
	}
	
	private Application app;
	private Logger logger;
	private InjectionContext injection;
	private SSLContext ssl;
	private List<TCPServer> servers = new ArrayList<>();
	private HTTPServerProtocol httpProtocol;
	private NetworkSessionProvider sessionProvider;
	private boolean sessionOnlyOnSecureChannel;
	private WebServerConfig config = null;
	private WebRequestRouter router = null;

	@Override
	public void close() {
		for (TCPServer server : servers)
			server.close();
		servers.clear();
		if (sessionProvider != null)
			try { sessionProvider.close(); }
			catch (IOException e) {
			}
		app.closed(this);
	}
	
	public void setSSLContext(SSLContext context) {
		ssl = context;
	}
	
	public void setConfiguration(WebServerConfig config) {
		this.config = config;
		// close current servers
		for (TCPServer server : servers)
			server.close();
		servers.clear();
		// configure routing
		router = new WebRequestRouter();
		router.setParent(root);
		for (WebRequestRouter.Configuration route : config.routing.route)
			router.addConfiguration(route);
		// start listening
		for (WebServerConfig.ListeningPort listen : config.listening.bind) {
			List<InetAddress> addresses;
			try {
				if (listen.macAddress == null) {
					// all mac addresses
					addresses = NetUtil.getAllIPs();
				} else {
					byte[] mac = NetUtil.MACFromString(listen.macAddress);
					addresses = NetUtil.getIPsFromMAC(mac);
				}
			} catch (Exception e) {
				logger.error("Error getting IP addresses to listen", e);
				continue;
			}
			List<InetAddress> ips;
			if (listen.ipAddresses != null && !listen.ipAddresses.isEmpty()) {
				ips = new ArrayList<>();
				for (byte[] ipa : listen.ipAddresses) {
					for (InetAddress addr : addresses) {
						if (!ArrayUtil.equals(ipa, addr.getAddress())) continue;
						ips.add(addr);
					}
				}
			} else
				ips = addresses;
			ServerProtocol protocol = httpProtocol;
			if (listen.secure) {
				try {
					if (ssl == null && config.ssl != null)
						ssl = SSLContextConfig.create(config.ssl);
					if (ssl == null)
						ssl = SSLContext.getDefault();
					protocol = new SSLServerProtocol(ssl, protocol);
				} catch (Throwable e) {
					logger.error("Unable to initialize SSL layer", e);
					continue;
				}
			}
			int port = listen.port;
			if (port <= 0) port = listen.secure ? 443 : 80;
			for (InetAddress address : ips) {
				@SuppressWarnings("resource")
				TCPServer server = new TCPServer();
				server.setProtocol(protocol);
				InetSocketAddress addr = new InetSocketAddress(address, port);
				try {
					server.bind(addr, listen.backlog);
				} catch (IOException e) {
					logger.error("Unable to listen to " + address);
					continue;
				}
				servers.add(server);
			}
		}
	}

	public Application getApplication() {
		return app;
	}
	
	public Logger getLogger() {
		return logger;
	}
	
	public NetworkSessionProvider getSessionProvider() {
		return sessionProvider;
	}
	
	public boolean isSessionOnlyAllowedOnSecureChannel() {
		return sessionOnlyOnSecureChannel;
	}
	
	public WebRequestProcessor getRootProcessor() {
		return root;
	}
	
	public InjectionContext getRootInjectionContext() {
		return injection;
	}
	
	public WebServerConfig getConfiguration() {
		return config;
	}
	
	public List<InetSocketAddress> getLocalAddresses() {
		LinkedList<InetSocketAddress> list = new LinkedList<>();
		for (TCPServer server : servers)
			list.addAll(server.getLocalAddresses());
		return list;
	}
	
	private final WebSessionProvider webSessionProvider = new WebSessionProvider() {
		
		@Override
		public AsyncWork<ISession, NoException> getSession(WebRequest request, boolean openIfNeeded) {
			if (sessionOnlyOnSecureChannel && !request.isSecure())
				return new AsyncWork<>(null, null);
			if (sessionProvider == null)
				return new AsyncWork<>(null, null);
			String id = request.getRequest().getCookie("lc-session");
			if (id != null) {
				id = id.trim();
				if (id.length() > 0) {
					AsyncWork<Session, NoException> getSession = sessionProvider.get(id, request.getClient());
					if (!getSession.isUnblocked()) {
						AsyncWork<ISession, NoException> result = new AsyncWork<>();
						getSession.listenAsync(new Task.Cpu.FromRunnable("Retrieve web session",  Task.PRIORITY_NORMAL, () -> {
							Session session = getSession.getResult();
							if (session != null) {
								request.getResponse().addCookie("lc-session", session.getId(), sessionProvider.getStorage().getExpiration(), "/", null, sessionOnlyOnSecureChannel, true);
								result.unblockSuccess(session);
							} else if (openIfNeeded) {
								session = sessionProvider.create(request.getClient());
								request.getResponse().addCookie("lc-session", session.getId(), sessionProvider.getStorage().getExpiration(), "/", null, sessionOnlyOnSecureChannel, true);
								result.unblockSuccess(session);
							} else
								result.unblockSuccess(null);
						}), true);
						return result;
					}
					Session session = getSession.getResult();
					if (session != null) {
						request.getResponse().addCookie("lc-session", session.getId(), sessionProvider.getStorage().getExpiration(), "/", null, sessionOnlyOnSecureChannel, true);
						return new AsyncWork<>(session, null);
					}
				}
			}
			if (openIfNeeded) {
				Session session = sessionProvider.create(request.getClient());
				request.getResponse().addCookie("lc-session", session.getId(), sessionProvider.getStorage().getExpiration(), "/", null, sessionOnlyOnSecureChannel, true);
				return new AsyncWork<>(session, null);
			}
			return new AsyncWork<>(null, null);
		}
		
		@Override
		public void saveSession(WebRequest request, ISession session) {
			sessionProvider.save((Session)session, request.getClient());
		}
		
		@Override
		public void removeSession(WebRequest request, ISession session) {
			if (sessionOnlyOnSecureChannel && !request.isSecure())
				return;
			if (session == null)
				return;
			sessionProvider.destroy((Session)session);
			request.getResponse().addCookie("lc-session", "", -365L * 24 * 60 * 60 * 1000, "/", null, sessionOnlyOnSecureChannel, true);
		}
	};
	
	private final HTTPRequestProcessor httpProcessor = new HTTPRequestProcessor() {
		
		@SuppressWarnings("resource")
		@Override
		public ISynchronizationPoint<?> process(TCPServerClient client, HTTPRequest request, HTTPServerResponse response) {
			TCPServer server = client.getServer();
			WebRequest req = new WebRequest(client, request, response, server.getProtocol() instanceof SSLServerProtocol, webSessionProvider);
			ISynchronizationPoint<?> res = root.process(Boolean.TRUE, req);
			res.listenAsync(new Task.Cpu.FromRunnable("Save client session", Task.PRIORITY_NORMAL, () -> { req.saveSession(); }), true);
			return res;
		}
	};
	
	private final WebRequestProcessor root = new WebRequestProcessor() {
		@Override
		public WebRequestProcessor getParent() {
			return null;
		}
		
		@Override
		public void setParent(WebRequestProcessor parent) {
		}
		
		@Override
		public InjectionContext getInjectionContext() {
			return injection;
		}
		
		@Override
		public Object checkProcessing(WebRequest request) {
			return Boolean.TRUE;
		}
		
		@Override
		public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
			String hostname = request.getRequest().getMIME().getFirstHeaderRawValue(HTTPRequest.HEADER_HOST);
			if (hostname == null) {
				request.getResponse().setStatus(400, "Bad Request, Host is missing");
				return new SynchronizationPoint<>(true);
			}
			if (router != null) {
				Object o = router.checkProcessing(request);
				if (o != null)
					return router.process(o, request);
			}
			request.getResponse().setStatus(404, "Not Found");
			return new SynchronizationPoint<>(true);
		}
		
		@Override
		public WebSocketHandler getWebSocketHandler(TCPServerClient client, HTTPRequest request, String path, String[] protocols) {
			String hostname = request.getMIME().getFirstHeaderRawValue(HTTPRequest.HEADER_HOST);
			if (hostname == null) return null;
			if (router != null) return router.getWebSocketHandler(client, request, path, protocols);
			return null;
		}
	};
	
}
