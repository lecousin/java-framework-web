package net.lecousin.framework.web;

import net.lecousin.framework.network.session.ISession;

public interface WebSessionProvider {
	
	ISession getSession(WebRequest request, boolean openIfNeeded);
	
	void removeSession(WebRequest request, ISession session);
	
	void saveSession(WebRequest request, ISession session);
	
}
