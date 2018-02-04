package net.lecousin.framework.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.serialization.annotations.MergeAttributes;
import net.lecousin.framework.io.serialization.annotations.Rename;
import net.lecousin.framework.io.serialization.annotations.TypeSerializer;
import net.lecousin.framework.network.NetUtil;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.websocket.WebSocketDispatcher.WebSocketHandler;
import net.lecousin.framework.network.server.TCPServerClient;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Triple;

public class WebRequestRouter implements WebRequestProcessor {

	public static class Configuration {
		/** List of IP addresses to answer, empty or null means all. */
		@TypeSerializer(NetUtil.IPSerializer.class)
		public List<byte[]> ipAddresses = null;
		
		/** List of hostnames to answer, empty or null means all. */
		public List<String> hostnames = null;
		
		/** Mapping between paths and processors.
		 * An empty path can be use for a default processor.
		 * Note that paths are checked in order, and the first eligible processor is used,
		 * so the order of the list may be important.
		 */
		@Rename(value=Pair.class, attribute="value1", newName="path")
		@MergeAttributes(type=Pair.class, target="value2")
		public List<Pair<String, WebRequestProcessor>> processorByPath = null;
	}
	
	public WebRequestRouter() {
	}
	
	private WebRequestProcessor parent = null;
	private List<Configuration> configs = new ArrayList<>();
	
	public void addConfiguration(Configuration config) {
		checkConfig(config);
		configs.add(config);
	}
	
	private void checkConfig(Configuration config) {
		if (config.hostnames != null)
			for (ListIterator<String> it = config.hostnames.listIterator(); it.hasNext(); ) {
				String host = it.next();
				host = host.trim().toLowerCase();
				if (host.isEmpty())
					it.remove();
				else
					it.set(host);
			}
		if (config.processorByPath != null)
			for (Pair<String, WebRequestProcessor> p : config.processorByPath) {
				if (p.getValue1().length() > 0 && !p.getValue1().endsWith("/"))
					p.setValue1(p.getValue1() + '/');
				p.getValue2().setParent(this);
			}
	}
	
	@Override
	public WebRequestProcessor getParent() {
		return parent;
	}
	
	@Override
	public void setParent(WebRequestProcessor parent) {
		this.parent = parent;
	}
	
	@Override
	public Object checkProcessing(WebRequest request) {
		String hostname = request.getRequest().getMIME().getFirstHeaderRawValue(HTTPRequest.HEADER_HOST);
		if (hostname == null)
			return null;
		int i = hostname.indexOf(':');
		if (i > 0) hostname = hostname.substring(0, i);
		hostname = hostname.trim().toLowerCase();
		InetSocketAddress localAddr;
		try { localAddr = (InetSocketAddress)request.getClient().getLocalAddress(); }
		catch (IOException e) { return null; }
		for (Configuration cfg : configs) {
			if (cfg.hostnames != null && !cfg.hostnames.isEmpty())
				if (!cfg.hostnames.contains(hostname))
					continue;
			if (cfg.ipAddresses != null && !cfg.ipAddresses.isEmpty()) {
				boolean ok = false;
				for (byte[] a : cfg.ipAddresses)
					if (ArrayUtil.equals(a, localAddr.getAddress().getAddress())) {
						ok = true;
						break;
					}
				if (!ok) continue;
			}
			if (cfg.processorByPath != null)
				for (Pair<String, WebRequestProcessor> p : cfg.processorByPath) {
					if (request.getSubPath().startsWith(p.getValue1())) {
						String myPath = request.getCurrentPath();
						String subPath = request.getSubPath();
						request.setPath(myPath + p.getValue1(), subPath.substring(p.getValue1().length()));
						Object o = p.getValue2().checkProcessing(request);
						request.setPath(myPath, subPath);
						if (o != null)
							return new Triple<>(p.getValue1(), p.getValue2(), o);
					}
				}
		}
		return null;
	}
	
	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		@SuppressWarnings("unchecked")
		Triple<String, WebRequestProcessor, Object> t = (Triple<String, WebRequestProcessor, Object>)fromCheck;
		request.setPath(request.getCurrentPath() + t.getValue1(), request.getSubPath().substring(t.getValue1().length()));
		return t.getValue2().process(t.getValue3(), request);
	}
	
	@Override
	public WebSocketHandler getWebSocketHandler(TCPServerClient client, HTTPRequest request, String path, String[] protocols) {
		String hostname = request.getMIME().getFirstHeaderRawValue(HTTPRequest.HEADER_HOST);
		if (hostname == null)
			return null;
		int i = hostname.indexOf(':');
		if (i > 0) hostname = hostname.substring(0, i);
		hostname = hostname.trim().toLowerCase();
		InetSocketAddress localAddr;
		try { localAddr = (InetSocketAddress)client.getLocalAddress(); }
		catch (IOException e) { return null; }
		for (Configuration cfg : configs) {
			if (cfg.hostnames != null && !cfg.hostnames.isEmpty())
				if (!cfg.hostnames.contains(hostname))
					continue;
			if (cfg.ipAddresses != null && !cfg.ipAddresses.isEmpty()) {
				boolean ok = false;
				for (byte[] a : cfg.ipAddresses)
					if (ArrayUtil.equals(a, localAddr.getAddress().getAddress())) {
						ok = true;
						break;
					}
				if (!ok) continue;
			}
			if (cfg.processorByPath != null)
				for (Pair<String, WebRequestProcessor> p : cfg.processorByPath) {
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
		}
		return null;
	}
	
}
