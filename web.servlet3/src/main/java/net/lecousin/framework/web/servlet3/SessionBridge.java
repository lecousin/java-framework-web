package net.lecousin.framework.web.servlet3;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpSession;

import net.lecousin.framework.network.session.ISession;

public class SessionBridge implements ISession {

	public SessionBridge(HttpSession session) {
		this.session = session;
	}
	
	protected HttpSession session;

	@Override
	public Serializable getData(String key) {
		return (Serializable)session.getAttribute(key);
	}

	@Override
	public void putData(String key, Serializable data) {
		session.setAttribute(key, data);
	}
	
	@Override
	public void removeData(String key) {
		session.removeAttribute(key);
	}
	
	@Override
	public Set<String> getKeys() {
		Enumeration<String> e = session.getAttributeNames();
		Set<String> keys = new HashSet<>();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			if (session.getAttribute(key) instanceof Serializable)
				keys.add(key);
		}
		return keys;
	}
	
}
