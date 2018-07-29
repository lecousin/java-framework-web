package net.lecousin.framework.web.test.server;

import org.junit.Assert;
import org.junit.Test;

import net.lecousin.framework.io.IO;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.HTTPRequest.Method;
import net.lecousin.framework.network.http.client.HTTPClient;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.test.AbstractTest;

public class TestVersionRedirection extends AbstractTest {

	@Test(timeout=120000)
	public void testVersionRedirection() throws Exception {
		Pair<HTTPClient, HTTPResponse> p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, BASE_HTTP_URL + "/version_redirection/test", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(3, p.getValue2().getStatusCode() / 100);
		Assert.assertEquals(CONTEXT_ROOT + "/version_redirection/1.0/test", p.getValue2().getMIME().getFirstHeaderRawValue("Location"));

		p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, BASE_HTTP_URL + "/version_redirection/test/0.1/hello", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(3, p.getValue2().getStatusCode() / 100);
		Assert.assertEquals(CONTEXT_ROOT + "/version_redirection/1.0/0.1/hello", p.getValue2().getMIME().getFirstHeaderRawValue("Location"));

		p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, BASE_HTTP_URL + "/version_redirection/0.1/hello", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(3, p.getValue2().getStatusCode() / 100);
		Assert.assertEquals(CONTEXT_ROOT + "/version_redirection/1.0/hello", p.getValue2().getMIME().getFirstHeaderRawValue("Location"));

		p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, BASE_HTTP_URL + "/version_redirection/1.0/test", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(404, p.getValue2().getStatusCode());
		Assert.assertNull(p.getValue2().getMIME().getFirstHeaderRawValue("Location"));

		p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, BASE_HTTP_URL + "/version_redirection/", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(3, p.getValue2().getStatusCode() / 100);
		Assert.assertEquals(CONTEXT_ROOT + "/version_redirection/1.0/", p.getValue2().getMIME().getFirstHeaderRawValue("Location"));

		p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, BASE_HTTP_URL + "/version_redirection", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(3, p.getValue2().getStatusCode() / 100);
		Assert.assertEquals(CONTEXT_ROOT + "/version_redirection/1.0/", p.getValue2().getMIME().getFirstHeaderRawValue("Location"));
	}

}
