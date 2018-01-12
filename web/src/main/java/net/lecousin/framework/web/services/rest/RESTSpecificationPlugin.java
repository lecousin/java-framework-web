package net.lecousin.framework.web.services.rest;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.plugins.Plugin;
import net.lecousin.framework.web.WebRequest;

public interface RESTSpecificationPlugin extends Plugin {

	/** Identifier, in lower case, that may be used in URL. */
	String getId();
	
	/** Name. */
	String getName();
	
	String getContentType();
	
	ISynchronizationPoint<Exception> generate(RESTWebServiceProvider provider, IO.Writable out, WebRequest request);
	
}
