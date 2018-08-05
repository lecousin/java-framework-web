package net.lecousin.framework.web.test.security;

import org.junit.Test;

import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.web.security.TokenRequest;
import net.lecousin.framework.web.test.AbstractTest;

public class TestSecurity extends AbstractTest {

	@Test(timeout=120000)
	public void test() throws Exception {
		HTTPClientUtil.GETfully(BASE_HTTP_URL + "/security/test?user=guillaume", 0).blockResult(0);
		HTTPClientUtil.GETfully(BASE_HTTP_URL + "/security/test?user=root", 0).blockResult(0);
		new TokenRequest();
	}
	
}
