package net.lecousin.framework.web;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import net.lecousin.framework.LCCoreVersion;
import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.Artifact;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.application.Version;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.network.http.HTTPRequest.Method;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClient;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.network.session.SessionInMemory;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.xml.serialization.XMLDeserializer;

import org.junit.Assert;
import org.junit.Test;

public class TestWebServer extends AbstractTest {

	public static void main(String[] args) {
		try {
			Application.start(new Artifact("net.lecousin.framework.test", "test", new Version(LCCoreVersion.VERSION)), true).block(0);
			initLogging();
			AsyncWork<WebServerConfig, Exception> loadConfig = XMLDeserializer.deserializeResource("test-webserver/server.xml", WebServerConfig.class, Task.PRIORITY_NORMAL);
			loadConfig.block(0);
			if (loadConfig.hasError())
				throw loadConfig.getError();
			WebServer server = new WebServer(null, new SessionInMemory(), 10 * 60 * 1000, true);
			server.setConfiguration(loadConfig.getResult());
			Thread.sleep(30 * 60 * 1000);
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}
	
	@Test(timeout=120000)
	public void testVersionRedirection() throws Exception {
		Pair<HTTPClient, HTTPResponse> p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, "http://localhost:1080/my_context/static/test", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(3, p.getValue2().getStatusCode() / 100);
		Assert.assertEquals("/my_context/static/1.0/test", p.getValue2().getMIME().getFirstHeaderRawValue("Location"));

		p = HTTPClientUtil.sendAndReceiveHeaders(Method.GET, "http://localhost:1080/my_context/static/test/0.1/hello", (IO.Readable)null).blockResult(0);
		p.getValue1().close();
		Assert.assertEquals(3, p.getValue2().getStatusCode() / 100);
		Assert.assertEquals("/my_context/static/1.0/0.1/hello", p.getValue2().getMIME().getFirstHeaderRawValue("Location"));
	}
	
	@Test(timeout=120000)
	public void testStaticIndex() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully("http://localhost:1080/my_context/static/1.0/", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		String expected = IOUtil.readFullyAsStringSync(LCCore.getApplication().getResource("test-webserver/static/index.html", Task.PRIORITY_NORMAL), StandardCharsets.UTF_8);
		Assert.assertEquals(expected, content);
	}
	
	@Test(timeout=120000)
	public void testStaticFile() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully("http://localhost:1080/my_context/static/1.0/file.txt", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		String expected = IOUtil.readFullyAsStringSync(LCCore.getApplication().getResource("test-webserver/static/file.txt", Task.PRIORITY_NORMAL), StandardCharsets.UTF_8);
		Assert.assertEquals(expected, content);
		// 1 hour cache
		Assert.assertEquals("public,max-age=" + (60 * 60 * 1000), p.getValue1().getMIME().getFirstHeaderRawValue("Cache-Control"));
	}
	
	@Test(timeout=120000)
	public void testStaticFromFileSystem() throws Exception {
		File tmp = File.createTempFile("test", "web");
		FileOutputStream out = new FileOutputStream(tmp);
		out.write("file system".getBytes());
		out.close();
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully("http://localhost:1080/my_context/static/1.0/fs/" + tmp.getName(), 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("file system", content);
	}
	
	@Test(timeout=120000)
	public void testTest1ProcessorAndCache() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully("http://localhost:1080/my_context/cached", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		Assert.assertTrue(p.getValue1().getMIME().getFirstHeaderRawValue("Cache-Control").contains("public"));
		
		p = HTTPClientUtil.GETfully("http://localhost:1080/my_context/cached/not", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		Assert.assertTrue(p.getValue1().getMIME().getFirstHeaderRawValue("Cache-Control").contains("no-cache"));
	}
	
	@Test(timeout=120000)
	public void testNeedAuthentication() throws Exception {
		try {
			HTTPClientUtil.GETfully("http://localhost:1080/my_context/need_auth/test1", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
		
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully("http://localhost:1080/my_context/need_auth/test1?user=guillaume", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		
		try {
			HTTPClientUtil.GETfully("http://localhost:1080/my_context/need_auth/test1?user=robert", 0).blockResult(0);
			throw new AssertionError("Error expected for unauthorized request");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
	}
	
	@Test(timeout=120000)
	public void testFilters() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully("http://localhost:1080/my_context/need_auth/test1?user=guillaume&pre-filter-1=hello", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		Assert.assertEquals("hello", p.getValue1().getMIME().getFirstHeaderRawValue("X-Pre-Filter-1"));
		Assert.assertEquals("hello", p.getValue1().getMIME().getFirstHeaderRawValue("X-Post-Filter-1"));
	}

}
