package net.lecousin.framework.web.servlet3;

import javax.servlet.http.HttpSession;

import net.lecousin.framework.network.session.ISession;

public class SessionBridge implements ISession {

	public SessionBridge(HttpSession session) {
		this.session = session;
	}
	
	protected HttpSession session;

	@Override
	public Object getData(String key) {
		return session.getAttribute(key);
	}

	@Override
	public void putData(String key, Object data) {
		session.setAttribute(key, data);
	}
	
}
