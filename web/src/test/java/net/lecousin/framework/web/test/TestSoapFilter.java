package net.lecousin.framework.web.test;

import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.services.soap.SOAPFilter;

public class TestSoapFilter implements SOAPFilter {

	@SOAPFilter.PreFilter
	public void preFilter(WebRequest req) {
		req.getResponse().addHeaderValue("X-SOAP-PreFiltered", "true");
	}
	
	@SOAPFilter.PostFilter
	public void postFilter(WebRequest req) {
		req.getResponse().addHeaderValue("X-SOAP-PostFiltered", "true");
	}
	
}
