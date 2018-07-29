package net.lecousin.framework.web.test;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.core.test.LCCoreAbstractTest;
import net.lecousin.framework.network.session.SessionInMemory;
import net.lecousin.framework.web.WebServer;
import net.lecousin.framework.web.WebServerConfig;
import net.lecousin.framework.xml.serialization.XMLDeserializer;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractTest extends LCCoreAbstractTest {

	private static WebServer server;
	
	public static final String HOST = "localhost";
	public static final int HTTP_PORT = 1080;
	public static final int HTTPS_PORT = 1443;
	public static final String CONTEXT_ROOT = "/test";
	
	public static final String BASE_HTTP_URL = "http://" + HOST + ":" + HTTP_PORT + CONTEXT_ROOT;
	public static final String BASE_HTTPS_URL = "https://" + HOST + ":" + HTTPS_PORT + CONTEXT_ROOT;
	
	@BeforeClass
	public static void initLogging(){
		LCCore.getApplication().getLoggerFactory().configure("classpath:test-webserver/logging.xml");		
	}

	@BeforeClass
	public static void startServer() throws Exception {
		AsyncWork<WebServerConfig, Exception> loadConfig = XMLDeserializer.deserializeResource("test-webserver/server.xml", WebServerConfig.class, Task.PRIORITY_NORMAL);
		loadConfig.block(0);
		if (loadConfig.hasError())
			throw loadConfig.getError();
		server = new WebServer(null, new SessionInMemory(), 10 * 60 * 1000, true);
		server.setConfiguration(loadConfig.getResult());
	}
	
	@AfterClass
	public static void stopServer() {
		server.close();
	}
	
}
