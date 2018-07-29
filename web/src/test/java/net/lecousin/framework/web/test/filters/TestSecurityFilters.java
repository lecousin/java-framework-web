package net.lecousin.framework.web.test.filters;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.test.AbstractTest;

public class TestSecurityFilters extends AbstractTest {

	@Test(timeout=120000)
	public void testNeedAuthentication() throws Exception {
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/test1", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/test1?user=guillaume", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/test1?user=robert", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/test1?user=nobody", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
	}
	
}
