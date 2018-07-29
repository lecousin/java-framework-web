package net.lecousin.framework.web.test.server;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.test.AbstractTest;

public class TestPrePostFilter extends AbstractTest {

	@Test(timeout=30000)
	public void testFilters() throws Exception {
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.GETfully(BASE_HTTP_URL + "/filters/prepost/test1?pre-filter-1=hello", 0).blockResult(0);
		String content = IOUtil.readFullyAsStringSync(p.getValue2(), StandardCharsets.UTF_8);
		Assert.assertEquals("This is test 1", content);
		Assert.assertEquals("hello", p.getValue1().getMIME().getFirstHeaderRawValue("X-Pre-Filter-1"));
		Assert.assertEquals("hello", p.getValue1().getMIME().getFirstHeaderRawValue("X-Post-Filter-1"));
	}

}
