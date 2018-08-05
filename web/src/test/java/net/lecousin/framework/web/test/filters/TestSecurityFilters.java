package net.lecousin.framework.web.test.filters;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.network.mime.MimeHeader;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.test.AbstractTest;

public class TestSecurityFilters extends AbstractTest {

	@Test(timeout=120000)
	public void testNeedAuthentication() throws Exception {
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		Pair<HTTPResponse, IO.Readable.Seekable> p;
		String content;
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?user=guillaume", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?auth-token=magic-token-guillaume", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("user", "guillaume")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("token", "magic-token-guillaume")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?user=robert&pass=test", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?auth-token=magic-token-robert", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("user", "robert"), new MimeHeader("pass", "hello")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("token", "magic-token-robert"), new MimeHeader("pass", "hello")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?user=dupont", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?auth-token=magic-token-dupont", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("User", "dupont")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("Token", "magic-token-dupont")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?user=durand", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?auth-token=magic-token-durand", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("USER", "durand")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("TOKEN", "magic-token-durand")).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?user=nobody", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?auth-token=magic-token-nobody", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1?auth-token=guillaume", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("user", "nobody")).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("token", "magic-token-nobody")).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/auth/test1", 0, new MimeHeader("token", "guillaume")).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
	}

	@Test(timeout=120000)
	public void testRole1() throws Exception {
		testAccess("role1", true, true, false, false, false);
	}

	@Test(timeout=120000)
	public void testRole2() throws Exception {
		testAccess("role2", true, false, true, false, false);
	}

	@Test(timeout=120000)
	public void testRole1and2() throws Exception {
		testAccess("role1and2", true, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testB1() throws Exception {
		testAccess("b1", true, true, false, false, false);
	}

	@Test(timeout=120000)
	public void testB1B2() throws Exception {
		testAccess("b1/b2", true, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testB1NotB2() throws Exception {
		testAccess("b1/notb2", false, true, false, false, false);
	}

	@Test(timeout=120000)
	public void testNotB1() throws Exception {
		testAccess("notb1", false, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testNotB1B2() throws Exception {
		testAccess("notb1/b2", false, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testNotB1NotB2() throws Exception {
		testAccess("notb1/notb2", false, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testB2() throws Exception {
		testAccess("b2", true, false, true, false, false);
	}

	@Test(timeout=120000)
	public void testB2B1() throws Exception {
		testAccess("b2/b1", true, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testB2NotB1() throws Exception {
		testAccess("b2/notb1", false, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testI1MoreThan20() throws Exception {
		testAccess("i1_more_than_20", true, false, false, false, false);
	}

	@Test(timeout=120000)
	public void testI1LessThan20() throws Exception {
		testAccess("i1_less_than_20", false, true, false, false, false);
	}
	
	private static void testAccess(String path, boolean guillaume, boolean robert, boolean dupont, boolean durand, boolean nobody) throws Exception {
		testAccess(path, null, nobody);
		testAccess(path, "guillaume", guillaume);
		testAccess(path, "robert", robert);
		testAccess(path, "dupont", dupont);
		testAccess(path, "durand", durand);
		testAccess(path, "nobody", nobody);
		testAccess(path, "root", true);
	}
	
	private static void testAccess(String path, String username, boolean accessExpected) throws Exception {
		String url = BASE_HTTP_URL + "/filters/security/" + path + "/test1";
		if (username != null) url += "?user=" + username;
		
		try {
			Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(url, 0).blockResult(0);
			if (!accessExpected)
				throw new AssertionError("Access granted for user " + username + " on path " + path);
			String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
			Assert.assertEquals("This is test 1", content);
		} catch (HTTPResponseError e) {
			if (accessExpected)
				throw new AssertionError("Access denied for user " + username + " on path " + path, e);
			Assert.assertEquals(403, e.getStatusCode());
		}
	}
	
	@Test(timeout=60000)
	public void testSession() throws Exception {
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/session/test1", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		Pair<HTTPResponse, IO.Readable.Seekable> p;
		String content;
		String sessionId;
		
		// no session on http
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/session/test1?user=guillaume", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		sessionId = p.getValue1().getCookie("lc-session");
		Assert.assertNull(sessionId);

		try {
			HTTPClientUtil.GETfully(BASE_HTTPS_URL + "/filters/security/session/test1", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		p = HTTPClientUtil.GETfully(BASE_HTTPS_URL + "/filters/security/session/test1?user=guillaume", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		sessionId = p.getValue1().getCookie("lc-session");
		Assert.assertNotNull(sessionId);
	
		// no session on http
		try {
			HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/security/session/test1", 0, new MimeHeader("Cookie", "lc-session=" + sessionId)).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		p = HTTPClientUtil.GETfully(BASE_HTTPS_URL + "/filters/security/session/test1", 0, new MimeHeader("Cookie", "lc-session=" + sessionId)).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
	}
	
}
