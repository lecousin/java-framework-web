package net.lecousin.framework.web;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.core.test.LCCoreAbstractTest;
import net.lecousin.framework.network.session.SessionInMemory;
import net.lecousin.framework.xml.serialization.XMLDeserializer;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractTest extends LCCoreAbstractTest {

	private static WebServer server;
	
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
