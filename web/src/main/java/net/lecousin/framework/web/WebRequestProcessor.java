package net.lecousin.framework.web;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.injection.InjectionContext;
import net.lecousin.framework.io.serialization.annotations.AddAttribute;
import net.lecousin.framework.io.serialization.annotations.Transient;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.websocket.WebSocketDispatcher.WebSocketHandler;
import net.lecousin.framework.network.http.websocket.WebSocketDispatcher.WebSocketRouter;
import net.lecousin.framework.network.server.TCPServerClient;

@AddAttribute(name="config", deserializer="configure", serializer="")
public interface WebRequestProcessor extends WebSocketRouter {

	/** Return the parent processor. */
	@Transient
	WebRequestProcessor getParent();
	
	/** Set the parent processor. This method must be called by the parent itself. */
	@Transient
	void setParent(WebRequestProcessor parent);

	@Transient
	default InjectionContext getInjectionContext() {
		WebRequestProcessor parent = getParent();
		if (parent != null)
			return parent.getInjectionContext();
		return LCCore.getApplication().getInstance(InjectionContext.class);
	}
	
	/** Check if this processor is the one to process the request.
	 * If null is returned, it means this processor does NOT process the request.
	 */
	Object checkProcessing(WebRequest request);
	
	/** Process the request. */
	ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request);
	
	@Override
	default WebSocketHandler getWebSocketHandler(TCPServerClient client, HTTPRequest request, String path, String[] protocols) {
		return null;
	}

	/** Configure this processor from the given file which may be an accessible resource in the classpath, or a file in the filesystem. */
	default void configure(String filename) throws Exception {
		throw new Exception("Configuration from file " + filename + " not implemented in " + getClass().getName());
	}
	
}
