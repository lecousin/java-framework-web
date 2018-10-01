package net.lecousin.framework.web;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.network.TCPRemote;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.server.HTTPServerResponse;
import net.lecousin.framework.network.session.ISession;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.IAuthenticationRequest;

public class WebRequest {

	public WebRequest(
		TCPRemote client,
		HTTPRequest request,
		HTTPServerResponse response,
		boolean onSecureChannel,
		WebSessionProvider sessionProvider
	) {
		this.client = client;
		this.request = request;
		this.response = response;
		this.onSecureChannel = onSecureChannel;
		this.sessionProvider = sessionProvider;
		try { this.fullPath = URLDecoder.decode(request.getPath(), "UTF-8"); }
		catch (UnsupportedEncodingException e) { /* cannot happen */ }
		this.currentPath = "/";
		this.subPath = fullPath.substring(1);
	}
	
	private TCPRemote client;
	private HTTPRequest request;
	private HTTPServerResponse response;
	private boolean onSecureChannel;
	private WebSessionProvider sessionProvider;
	
	private String fullPath;
	private String currentPath;
	private String subPath;
	
	private AsyncWork<ISession, NoException> session;
	private boolean sessionRequested = false;
	
	private Map<IAuthenticationProvider, IAuthentication> auth = null;
	private List<IAuthenticationRequest> authRequests = new LinkedList<>();
	
	public TCPRemote getClient() {
		return client;
	}
	
	public HTTPRequest getRequest() {
		return request;
	}
	
	public HTTPServerResponse getResponse() {
		return response;
	}
	
	public boolean isSecure() {
		return onSecureChannel;
	}
	
	public String getFullPath() {
		return fullPath;
	}
	
	public String getCurrentPath() {
		return currentPath;
	}
	
	public String getSubPath() {
		return subPath;
	}
	
	public void setPath(String current, String sub) {
		this.currentPath = current;
		this.subPath = sub;
	}
	
	public String getRootURL() {
		StringBuilder url = new StringBuilder();
		url.append("http");
		if (isSecure()) url.append('s');
		url.append("://").append(request.getMIME().getFirstHeaderRawValue(HTTPRequest.HEADER_HOST));
		return url.toString();
	}
	
	/** Return the session. */
	public AsyncWork<ISession, NoException> getSession(boolean openIfNeeded) {
		// first request
		if (session == null)
			return session = sessionProvider.getSession(this, openIfNeeded);
		// if not mandatory, just return the previous request
		if (!openIfNeeded)
			return session;
		// if previously requested as mandatory, just return the previous request
		if (sessionRequested)
			return session;
		// if mandatory, we may need to request again
		sessionRequested = true;
		AsyncWork<ISession, NoException> prevRequest = session;
		session = new AsyncWork<>();
		prevRequest.listenInline((s) -> {
			if (s != null) session.unblockSuccess(s);
			else sessionProvider.getSession(this, true).listenInline(session);
		});
		return session;
	}
	
	void saveSession() {
		if (session == null) return;
		session.listenInline((s) -> {
			if (s != null)
				sessionProvider.saveSession(this, s);
		});
	}
	
	public void removeSession() {
		if (session == null && !sessionRequested)
			getSession(true);
		try {
			if (session == null || session.blockResult(0) == null)
				return;
		} catch (Exception e) {
			return;
		}
		sessionProvider.removeSession(this, session.getResult());
		session = null;
		sessionRequested = false;
	}
	
	public boolean hasAuthentication(IAuthenticationProvider provider) {
		return auth != null && auth.containsKey(provider);
	}
	
	public IAuthentication getAuthentication(IAuthenticationProvider provider) {
		if (auth == null || !auth.containsKey(provider))
			return null;
		return auth.get(provider);
	}
	
	public AsyncWork<IAuthentication, Exception> authenticate(IAuthenticationProvider provider) {
		if (auth != null && auth.containsKey(provider))
			return new AsyncWork<>(auth.get(provider), null);
		ISynchronizationPoint<Exception> a = provider.authenticate(this);
		if (a.isUnblocked()) {
			if (a.hasError()) return new AsyncWork<>(null, a.getError());
			if (auth == null) return new AsyncWork<>(null, null);
			return new AsyncWork<>(auth.get(provider), null);
		}
		AsyncWork<IAuthentication, Exception> result = new AsyncWork<>();
		a.listenInline(() -> {
			if (auth == null) result.unblockSuccess(null);
			else result.unblockSuccess(auth.get(provider));
		}, result);
		return result;
	}
	
	public void addAuthentication(IAuthenticationProvider provider, IAuthentication authentication) {
		if (auth == null) auth = new HashMap<>(5);
		auth.put(provider, authentication);
	}
	
	public void clearAuthentication(IAuthenticationProvider provider) {
		if (auth != null)
			provider.deconnect(auth.remove(provider), this);
		else
			provider.deconnect(null, this);
	}
	
	public void addAuthenticationRequest(IAuthenticationRequest auth) {
		authRequests.add(auth);
	}
	
	public List<IAuthenticationRequest> getAuthenticationRequests() {
		return authRequests;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends IAuthenticationRequest> T getAuthenticationRequest(Class<T> type) {
		for (IAuthenticationRequest r : authRequests)
			if (type.isAssignableFrom(r.getClass()))
				return (T)r;
		return null;
	}
	
}
