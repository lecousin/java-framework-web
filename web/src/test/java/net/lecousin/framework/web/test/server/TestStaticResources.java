package net.lecousin.framework.web.test.server;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.StaticResourcesProcessor;
import net.lecousin.framework.web.test.AbstractTest;

public class TestStaticResources extends AbstractTest {

	@Test(timeout=30000)
	public void testBasics() {
		StaticResourcesProcessor p = new StaticResourcesProcessor(new File("."));
		Assert.assertNotNull(p.getFromFileSystem());
		Assert.assertNull(p.getFromClassPath());
		Assert.assertNull(p.getDirectoryPages());
		Assert.assertNull(p.getRestrictPatterns());
		p.setFromFileSystem("test");
		Assert.assertEquals("test", p.getFromFileSystem());
		p.setFromClassPath("test2");
		Assert.assertEquals("test2", p.getFromClassPath());
		p.setDirectoryPages(Collections.singletonList("toto"));
		p.setRestrictPatterns(Collections.singletonList("toto"));
		p = new StaticResourcesProcessor("test");
		Assert.assertEquals("test", p.getFromClassPath());
		Assert.assertNull(p.getFromFileSystem());
		Assert.assertNull(p.getParent());
	}
	
	@Test(timeout=120000)
	public void testStaticIndex() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static/", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		String expected = IOUtil.readFullyAsStringSync(LCCore.getApplication().getResource("test-webserver/static/index.html", Task.PRIORITY_NORMAL), StandardCharsets.UTF_8);
		Assert.assertEquals(expected, content);

		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		expected = IOUtil.readFullyAsStringSync(LCCore.getApplication().getResource("test-webserver/static/index.html", Task.PRIORITY_NORMAL), StandardCharsets.UTF_8);
		Assert.assertEquals(expected, content);
	}
	
	@Test(timeout=120000)
	public void testStaticFile() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static/file.txt", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		String expected = IOUtil.readFullyAsStringSync(LCCore.getApplication().getResource("test-webserver/static/file.txt", Task.PRIORITY_NORMAL), StandardCharsets.UTF_8);
		Assert.assertEquals(expected, content);
		// no cache
		Assert.assertNull(p.getValue1().getMIME().getFirstHeaderRawValue("Cache-Control"));
	}
	
	@Test(timeout=120000)
	public void testStaticFileWithCache() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static/cached/file.txt", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		String expected = IOUtil.readFullyAsStringSync(LCCore.getApplication().getResource("test-webserver/static/file.txt", Task.PRIORITY_NORMAL), StandardCharsets.UTF_8);
		Assert.assertEquals(expected, content);
		// 1 hour cache
		Assert.assertEquals("public,max-age=" + (60 * 60 * 1000), p.getValue1().getMIME().getFirstHeaderRawValue("Cache-Control"));
	}
	
	@Test(timeout=120000)
	public void testStaticFromFileSystem() throws Exception {
		File tmp = File.createTempFile("test", "web");
		tmp.deleteOnExit();
		FileOutputStream out = new FileOutputStream(tmp);
		out.write("file system".getBytes());
		out.close();
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static/fs/" + tmp.getName(), 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("file system", content);
		
		tmp = new File(tmp.getParentFile(), "test_web_static");
		tmp.mkdir();
		tmp = new File(tmp, "index.html");
		tmp.deleteOnExit();
		out = new FileOutputStream(tmp);
		out.write("file system2".getBytes());
		out.close();
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static/fs2/", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("file system2", content);
	}

	@Test(timeout=120000)
	public void testRestrictedPatterns() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static/restricted/index.html", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		String expected = IOUtil.readFullyAsStringSync(LCCore.getApplication().getResource("test-webserver/static/index.html", Task.PRIORITY_NORMAL), StandardCharsets.UTF_8);
		Assert.assertEquals(expected, content);
		
		try {
			p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/static/restricted/file.txt", 0).blockResult(0);
			throw new AssertionError("Resource must be restricted");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(404, e.getStatusCode());
		}
	}
	
}
