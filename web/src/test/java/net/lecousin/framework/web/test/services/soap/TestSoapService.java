package net.lecousin.framework.web.test.services.soap;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.soap.SOAP;
import net.lecousin.framework.xml.dom.XMLDocument;
import net.lecousin.framework.xml.dom.XMLElement;

import org.w3c.dom.Element;

@SOAP.Service(
	defaultPath="testSoap",
	targetNamespace="http://test"
)
@WebService.Description("This is a SOAP service for testing purposes.")
public class TestSoapService implements SOAP {

	public static class TestRequest {
		public String name;
	}

	public static class TestResult {
		public String hello;
	}

	public static class TestMyHeader {
		public String message;
	}
	
	@SOAP.Message
	public static class TestRequestWithHeader {
		
		@SOAP.MessageBody
		public TestRequest body;

		@SOAP.Header(localName="reqH1")
		public TestMyHeader h1;

		@SOAP.Header(localName="reqH2",namespaceURI="http://customURI")
		public TestMyHeader h2;
	}
	
	@SOAP.Message
	public static class TestResultWithHeader {
		
		@SOAP.MessageBody
		public TestResult body;

		@SOAP.Header(localName="resH1")
		public TestMyHeader h1;

		@SOAP.Header(localName="resH2",namespaceURI="http://customURI")
		public TestMyHeader h2;
	}
	
	@SOAP.Operation
	public TestResult helloWorld(@WebService.Body TestRequest req) {
		TestResult res = new TestResult();
		res.hello = "Hello " + req.name;
		return res;
	}
	
	@SOAP.Operation(action="helloFromQuery")
	public AsyncWork<TestResult, Exception> helloWorldFromQuery(@WebService.Query(name="name", required=true) String name) {
		AsyncWork<TestResult, Exception> result = new AsyncWork<>();
		new Task.Cpu.FromRunnable("Test", Task.PRIORITY_LOW, () -> {
			TestResult res = new TestResult();
			res.hello = "Hello " + name;
			result.unblockSuccess(res);
		}).start();
		return result;
	}
	
	@SOAP.Operation
	public @SOAP.BodyElement Element testAnyInputAndOutput(@SOAP.BodyElement Element request) {
		XMLElement element = new XMLElement(new XMLDocument(), "", "myResponseTo" + request.getLocalName());
		element.appendChild(request);
		return element;
	}
	
	@SOAP.Operation
	public TestResult testWithHeader(@WebService.Body TestRequest req, @SOAP.Header(localName="myHeader") TestMyHeader myHeader1, @SOAP.Header(localName="myHeader",namespaceURI="http://customURI") TestMyHeader myHeader2) {
		TestResult res = new TestResult();
		res.hello = "Hello " + req.name;
		if (myHeader1 != null)
			res.hello += ", " + myHeader1.message;
		if (myHeader2 != null)
			res.hello += ", " + myHeader2.message;
		return res;
	}
	
	@SOAP.Operation
	public TestResultWithHeader testWithHeader2(@WebService.Body TestRequest req, @SOAP.Header(localName="myHeader") TestMyHeader myHeader1, @SOAP.Header(localName="myHeader",namespaceURI="http://customURI") TestMyHeader myHeader2) {
		TestResultWithHeader res = new TestResultWithHeader();
		res.body = new TestResult();
		res.body.hello = "Hello " + req.name;
		if (myHeader1 != null) {
			res.h1 = new TestMyHeader();
			res.h1.message = "The message of this header was: " + myHeader1.message;
		}
		if (myHeader2 != null) {
			res.h2 = new TestMyHeader();
			res.h2.message = "The message of this header was: " + myHeader2.message;
		}
		return res;
	}
	
	@SOAP.Operation
	public TestResultWithHeader testWithHeader3(TestRequestWithHeader req) {
		TestResultWithHeader res = new TestResultWithHeader();
		res.body = new TestResult();
		res.body.hello = "Hello " + req.body.name;
		if (req.h1 != null) {
			res.h1 = new TestMyHeader();
			res.h1.message = "The message of this header was: " + req.h1.message;
		}
		if (req.h2 != null) {
			res.h2 = new TestMyHeader();
			res.h2.message = "The message of this header was: " + req.h2.message;
		}
		return res;
	}
	
	@SOAP.Operation
	@WebService.RequireRole("Role1")
	public TestResult helloWorldWithAuthentication(@WebService.Body TestRequest req, IAuthentication auth) {
		TestResult res = new TestResult();
		res.hello = "Hello " + req.name;
		if (auth == null)
			res.hello += ", you are not authenticated!";
		else
			res.hello += ", you are well authenticated as " + auth.getUsername();
		return res;
	}
	
}
