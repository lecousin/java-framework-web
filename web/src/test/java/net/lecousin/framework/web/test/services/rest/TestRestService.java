package net.lecousin.framework.web.test.services.rest;

import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.rest.REST;

@REST.Resource(type=REST.ResourceType.INDIVIDUAL, path="test")
@WebService.Description("This is a REST service as an individual resource for testing purposes.")
public class TestRestService implements REST {

	@REST.GetResource
	public String get() {
		return "Hello tester";
	}
	
}
