package net.lecousin.framework.web;

import java.util.ArrayList;
import java.util.Collections;

import net.lecousin.framework.io.serialization.annotations.TypeSerializer;
import net.lecousin.framework.network.NetUtil;
import net.lecousin.framework.network.ssl.SSLContextConfig;
import net.lecousin.framework.util.Pair;

public class WebServerConfig {
	
	public Listening listening = new Listening();
	public Routing routing = new Routing();
	public SSLContextConfig ssl = null;
	
	public static class Listening {

		public ArrayList<ListeningPort> bind = new ArrayList<>();
		
	}
	
	public static class Routing {

		public ArrayList<WebRequestRouter.Configuration> route = new ArrayList<>();
		
	}

	public static class ListeningPort {
		
		public ListeningPort() {}
		public ListeningPort(int port) {
			this.port = port;
		}
		public ListeningPort(int port, boolean secure) {
			this.port = port;
			this.secure = secure;
		}
		public ListeningPort(byte[] ip, int port) {
			ipAddresses.add(ip);
			this.port = port;
		}
		public ListeningPort(byte[] ip, int port, boolean secure) {
			ipAddresses.add(ip);
			this.port = port;
			this.secure = secure;
		}
		
		/** Specific MAC address of a network interface, null or empty mean all. */
		public String macAddress = null;
		/** List of IP addresses, using IPv4 or IPv6 notation, null or empty means all. */
		@TypeSerializer(NetUtil.IPSerializer.class)
		public ArrayList<byte[]> ipAddresses = new ArrayList<>();
		/** True to activate SSL layer (HTTPS instead of HTTP). */
		public boolean secure = false;
		/** Port to listen to, 0 or negative value means default port (80 or 443). */
		public int port = -1;
		/** Maximum number of pending connections, or 0. */
		public int backlog = 0;
		
	}
	
	public static WebServerConfig getDefault(WebRequestProcessor processor) {
		WebServerConfig cfg = new WebServerConfig();
		ListeningPort l = new ListeningPort();
		cfg.listening.bind.add(l); // listen on all interfaces and all ips on port 80
		l = new ListeningPort();
		l.secure = true;
		cfg.listening.bind.add(l); // listen on all interfaces and all ips on port 443 with SSL
		WebRequestRouter.Configuration route = new WebRequestRouter.Configuration();
		route.processorByPath = Collections.singletonList(new Pair<>("", processor));
		cfg.routing.route.add(route);
		return cfg;
	}
	
	
}
