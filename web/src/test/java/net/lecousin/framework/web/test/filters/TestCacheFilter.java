package net.lecousin.framework.web.test.filters;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.test.AbstractTest;

public class TestCacheFilter extends AbstractTest {

	@Test(timeout=120000)
	public void testTest1ProcessorAndCache() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/cache", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		Assert.assertTrue(p.getValue1().getMIME().getFirstHeaderRawValue("Cache-Control").contains("public"));
		
		p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/cache/not", 0).blockResult(0);
		content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		Assert.assertTrue(p.getValue1().getMIME().getFirstHeaderRawValue("Cache-Control").contains("no-cache"));
	}
	
}
