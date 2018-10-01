package net.lecousin.framework.web.test;

import net.lecousin.framework.LCCoreVersion;
import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.Artifact;
import net.lecousin.framework.application.Version;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.network.session.SessionInMemory;
import net.lecousin.framework.web.WebServer;
import net.lecousin.framework.web.WebServerConfig;
import net.lecousin.framework.xml.serialization.XMLDeserializer;

public class TestWebServer extends AbstractTest {

	public static void main(String[] args) {
		try {
			Application.start(new Artifact("net.lecousin.framework.test", "test", new Version(LCCoreVersion.VERSION)), true).block(0);
			initLogging();
			AsyncWork<WebServerConfig, Exception> loadConfig = XMLDeserializer.deserializeResource("test-webserver/server.xml", WebServerConfig.class, Task.PRIORITY_NORMAL);
			loadConfig.block(0);
			if (loadConfig.hasError())
				throw loadConfig.getError();
			WebServer server = new WebServer(null, new SessionInMemory(10 * 60 * 1000), true);
			server.setConfiguration(loadConfig.getResult());
			Thread.sleep(30 * 60 * 1000);
			server.close();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

}
