package net.lecousin.framework.web;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.network.session.ISession;

public interface WebSessionProvider {
	
	AsyncWork<ISession, NoException> getSession(WebRequest request, boolean openIfNeeded);
	
	void removeSession(WebRequest request, ISession session);
	
	void saveSession(WebRequest request, ISession session);
	
}
