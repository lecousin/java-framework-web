package net.lecousin.framework.web.servlet3.test;

import net.lecousin.framework.web.services.rest.REST;

@REST.Resource(type=REST.ResourceType.INDIVIDUAL, path="test")
public class TestRestService implements REST {

	@REST.GetResource
	public String get() {
		return "Hello tester";
	}
	
}
