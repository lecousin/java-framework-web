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
	
	@REST.Method
	public String welcome(@WebService.Query(name="name", required=true) String name) {
		return "Welcome " + name + " !";
	}
	
	public static class Test1Input {
		public int i;
		public String text;
	}
	
	public static class Test1Output {
		public int result;
		public String answer;
	}
	
	@REST.Method
	public Test1Output test1(@WebService.Body Test1Input input) {
		Test1Output output = new Test1Output();
		output.result = input.i * 51;
		output.answer = "Hello " + input.text;
		return output;
	}
	
}
