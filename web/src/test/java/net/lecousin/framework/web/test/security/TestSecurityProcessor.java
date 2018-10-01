package net.lecousin.framework.web.test.security;

import org.junit.Assert;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestProcessor;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.IRightsManager;

public class TestSecurityProcessor implements WebRequestProcessor {

	@Inject
	public IAuthenticationProvider authenticationProvider;
	@Inject
	public IRightsManager rightsManager;
	private WebRequestProcessor parent;
	
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
		return Boolean.TRUE;
	}
	
	@SuppressWarnings("resource")
	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		try {
			IAuthentication auth = request.authenticate(authenticationProvider).blockResult(0);
			Assert.assertTrue(rightsManager.hasRight(auth, "i1", 51));
			Assert.assertEquals(auth.isSuperAdmin(), rightsManager.hasRight(auth, "i1", 12));
			Assert.assertEquals(auth.isSuperAdmin(), rightsManager.hasRight(auth, "toto", 12));
		} catch (Throwable t) {
			return new SynchronizationPoint<Exception>(new Exception("security test error", t));
		}
		request.getResponse().setStatus(200);
		request.getResponse().getMIME().setBodyToSend(new ByteArrayIO("OK".getBytes(), "test"));
		return new SynchronizationPoint<>(true);
	}
	
}
