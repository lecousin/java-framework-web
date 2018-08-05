package net.lecousin.framework.web.test.services.rest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.io.text.BufferedReadableCharacterStream;
import net.lecousin.framework.json.JSONParser;
import net.lecousin.framework.network.http.HTTPRequest.Method;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.network.mime.MimeHeader;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.test.AbstractTest;

public class TestREST extends AbstractTest {

	@Test(timeout=60000)
	public void testGet() throws Exception {
		Object result;
		
		result = send(Method.GET, "test", null, null);
		Assert.assertEquals("Hello tester", result);
		
		result = send(Method.POST, "test/welcome?name=test", null, null);
		Assert.assertEquals("Welcome test !", result);
		
		result = send(Method.POST, "test/test1", "{\"i\":3,\"text\":\"world\"}", "application/json");
		Assert.assertEquals(Long.valueOf(3 * 51), ((Map)result).get("result"));
		Assert.assertEquals("Hello world", ((Map)result).get("answer"));
	}
	
	private static Object send(Method method, String url, String body, String contentType) throws Exception {
		IO.Readable b;
		MimeHeader[] h;
		if (body == null) {
			b = null;
			h = new MimeHeader[0];
		} else {
			b = new ByteArrayIO(body.getBytes(StandardCharsets.UTF_8), "body");
			h = new MimeHeader[] { new MimeHeader("Content-Type", contentType) };
		}
		Pair<HTTPResponse, IO.Readable.Seekable> p = HTTPClientUtil.sendAndReceiveFully(method, BASE_HTTP_URL + "/services/" + url, b, h).blockResult(0);
		return JSONParser.parse(new BufferedReadableCharacterStream(p.getValue2(),StandardCharsets.UTF_8, 4096, 3), Task.PRIORITY_NORMAL).blockResult(0);
	}
	
}
