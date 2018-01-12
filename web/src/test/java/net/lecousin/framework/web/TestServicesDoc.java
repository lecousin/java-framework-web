package net.lecousin.framework.web;

import java.nio.charset.StandardCharsets;

import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.util.Pair;

import org.junit.Assert;
import org.junit.Test;

public class TestServicesDoc extends AbstractTest {

	@Test(timeout=30000)
	public void test() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GET("http://localhost:1080/my_context/services/", 3).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertTrue(content.contains("WSDL"));
	}
	
}
