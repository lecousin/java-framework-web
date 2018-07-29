package net.lecousin.framework.web.test.services.soap;

import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.eviware.soapui.impl.WsdlInterfaceFactory;
import com.eviware.soapui.impl.wsdl.WsdlInterface;
import com.eviware.soapui.impl.wsdl.WsdlOperation;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlRequest;
import com.eviware.soapui.impl.wsdl.WsdlSubmit;
import com.eviware.soapui.impl.wsdl.WsdlSubmitContext;
import com.eviware.soapui.model.iface.Response;

import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.security.LoginRequest;
import net.lecousin.framework.web.services.soap.SOAPClient;
import net.lecousin.framework.web.services.soap.SOAPMessageContent;
import net.lecousin.framework.web.test.AbstractTest;
import net.lecousin.framework.web.test.services.soap.TestSoapService.TestMyHeader;
import net.lecousin.framework.xml.dom.DOMUtil;

public class TestSOAP extends AbstractTest {

	@Test(timeout=120000)
	public void testSOAPHelloWorld() throws Exception {
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "World";
		TestSoapService.TestResult response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "helloWorld", req, "http://testRequest", TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello World", response.hello);
	}

	@Test(timeout=120000)
	public void testSOAPHelloWorldFromQuery() throws Exception {
		TestSoapService.TestResult response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap?name=World+of+query", "helloFromQuery", null, "http://testRequest", TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello World of query", response.hello);
	}

	@Test(timeout=120000)
	public void testSOAPAnyInputAndOutput() throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element req = doc.createElement("MyElement");
		req.setAttribute("hello", "world");
		Element response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "testAnyInputAndOutput", req, null, Element.class, null, new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("myResponseToMyElement", response.getLocalName());
		Element myElement = DOMUtil.getChild(response, "MyElement");
		Assert.assertNotNull(myElement);
		Assert.assertEquals("world", myElement.getAttribute("hello"));
	}

	@Test(timeout=120000)
	public void testSOAPWithHeader1() throws Exception {
		SOAPMessageContent request = new SOAPMessageContent();
		SOAPMessageContent.Header header;
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "Test1";
		request.bodyContent = req;
		request.bodyNamespaceURI = "http://testRequest";
		request.bodyLocalName = "TestRequest";
		request.bodyType = new TypeDefinition(TestSoapService.TestRequest.class);
		header = new SOAPMessageContent.Header();
		header.localName = "myHeader";
		header.namespaceURI = "http://test";
		TestMyHeader h = new TestMyHeader();
		h.message = "This is a test with header";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);
		
		TestSoapService.TestResult response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "testWithHeader", request, TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello Test1, This is a test with header", response.hello);
	}

	@Test(timeout=120000)
	public void testSOAPWithHeader2() throws Exception {
		SOAPMessageContent request = new SOAPMessageContent();
		SOAPMessageContent.Header header;
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "Test2";
		request.bodyContent = req;
		request.bodyNamespaceURI = "http://testRequest";
		request.bodyLocalName = "TestRequest";
		request.bodyType = new TypeDefinition(TestSoapService.TestRequest.class);
		
		header = new SOAPMessageContent.Header();
		header.localName = "myHeader";
		header.namespaceURI = "http://test";
		TestMyHeader h = new TestMyHeader();
		h.message = "This is a test with header";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);

		header = new SOAPMessageContent.Header();
		header.localName = "myHeader";
		header.namespaceURI = "http://customURI";
		h = new TestMyHeader();
		h.message = "this is custom";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);
		
		TestSoapService.TestResult response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "testWithHeader", request, TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello Test2, This is a test with header, this is custom", response.hello);
		
		Pair<TestSoapService.TestResult, HTTPResponse> resp = SOAPClient.sendAndGetHTTPResponse(BASE_HTTP_URL + "/services/testSoap", "testWithHeader", request, TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello Test2, This is a test with header, this is custom", resp.getValue1().hello);
		Assert.assertEquals("true", resp.getValue2().getMIME().getFirstHeaderRawValue("X-SOAP-PreFiltered"));
		Assert.assertEquals("true", resp.getValue2().getMIME().getFirstHeaderRawValue("X-SOAP-PostFiltered"));
	}

	@Test(timeout=120000)
	public void testSOAPWithHeader3() throws Exception {
		SOAPMessageContent request = new SOAPMessageContent();
		SOAPMessageContent.Header header;
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "Test3";
		request.bodyContent = req;
		request.bodyNamespaceURI = "http://testRequest";
		request.bodyLocalName = "TestRequest";
		request.bodyType = new TypeDefinition(TestSoapService.TestRequest.class);
		
		header = new SOAPMessageContent.Header();
		header.localName = "myHeader";
		header.namespaceURI = "http://customURI";
		TestMyHeader h = new TestMyHeader();
		h.message = "only custom";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);
		
		TestSoapService.TestResult response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "testWithHeader", request, TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello Test3, only custom", response.hello);
	}
	

	@Test(timeout=120000)
	public void testSOAPWithHeader4() throws Exception {
		SOAPMessageContent request = new SOAPMessageContent();
		SOAPMessageContent.Header header;
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "Test4";
		request.bodyContent = req;
		request.bodyNamespaceURI = "http://testRequest";
		request.bodyLocalName = "TestRequest";
		request.bodyType = new TypeDefinition(TestSoapService.TestRequest.class);
		
		header = new SOAPMessageContent.Header();
		header.localName = "nothing";
		header.namespaceURI = "http://test";
		TestMyHeader h = new TestMyHeader();
		h.message = "This is nothing";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);

		header = new SOAPMessageContent.Header();
		header.localName = "myHeader";
		header.namespaceURI = "http://customURI";
		h = new TestMyHeader();
		h.message = "this is custom";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);
		
		TestSoapService.TestResult response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "testWithHeader", request, TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello Test4, this is custom", response.hello);
	}
	

	@Test(timeout=120000)
	public void testSOAPWithHeader5() throws Exception {
		SOAPMessageContent request = new SOAPMessageContent();
		SOAPMessageContent.Header header;
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "Test5";
		request.bodyContent = req;
		request.bodyNamespaceURI = "http://testRequest";
		request.bodyLocalName = "TestRequest";
		request.bodyType = new TypeDefinition(TestSoapService.TestRequest.class);
		
		header = new SOAPMessageContent.Header();
		header.localName = "myHeader";
		header.namespaceURI = "http://test";
		TestMyHeader h = new TestMyHeader();
		h.message = "This is a test with header";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);

		header = new SOAPMessageContent.Header();
		header.localName = "myHeader";
		header.namespaceURI = "http://customURI";
		h = new TestMyHeader();
		h.message = "this is custom";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);
		
		SOAPMessageContent response = new SOAPMessageContent();
		response.bodyNamespaceURI = "http://testResponse";
		response.bodyLocalName = "TestResult";
		response.bodyType = new TypeDefinition(TestSoapService.TestResult.class);
		header = new SOAPMessageContent.Header();
		header.localName = "resH2";
		header.namespaceURI = "http://customURI";
		header.contentType = new TypeDefinition(TestMyHeader.class);
		response.headers.add(header);
		SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "testWithHeader2", request, response, new ArrayList<>(0)).blockThrow(0);
		Assert.assertTrue(response.bodyContent instanceof TestSoapService.TestResult);
		Assert.assertEquals("Hello Test5", ((TestSoapService.TestResult)response.bodyContent).hello);
		Assert.assertNotNull(response.headers.get(0).content);
		Assert.assertTrue(response.headers.get(0).content instanceof TestMyHeader);
		Assert.assertEquals("The message of this header was: this is custom", ((TestMyHeader)response.headers.get(0).content).message);
	}


	@Test(timeout=120000)
	public void testSOAPWithHeader6() throws Exception {
		SOAPMessageContent request = new SOAPMessageContent();
		SOAPMessageContent.Header header;
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "Test6";
		request.bodyContent = req;
		request.bodyNamespaceURI = "http://testRequest";
		request.bodyLocalName = "TestRequest";
		request.bodyType = new TypeDefinition(TestSoapService.TestRequest.class);
		
		header = new SOAPMessageContent.Header();
		header.localName = "reqH1";
		header.namespaceURI = "http://test";
		TestMyHeader h = new TestMyHeader();
		h.message = "test again";
		header.content = h;
		header.contentType = new TypeDefinition(TestMyHeader.class);
		request.headers.add(header);

		SOAPMessageContent response = new SOAPMessageContent();
		response.bodyNamespaceURI = "http://testResponse";
		response.bodyLocalName = "TestResult";
		response.bodyType = new TypeDefinition(TestSoapService.TestResult.class);
		header = new SOAPMessageContent.Header();
		header.localName = "resH1";
		header.namespaceURI = "http://test";
		header.contentType = new TypeDefinition(TestMyHeader.class);
		response.headers.add(header);
		SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "testWithHeader3", request, response, new ArrayList<>(0)).blockThrow(0);
		Assert.assertTrue(response.bodyContent instanceof TestSoapService.TestResult);
		Assert.assertEquals("Hello Test6", ((TestSoapService.TestResult)response.bodyContent).hello);
		Assert.assertNotNull(response.headers.get(0).content);
		Assert.assertTrue(response.headers.get(0).content instanceof TestMyHeader);
		Assert.assertEquals("The message of this header was: test again", ((TestMyHeader)response.headers.get(0).content).message);
	}
	
	@Test(timeout=120000)
	public void testSOAPhelloWorldWithAuthentication() throws Exception {
		SOAPMessageContent request = new SOAPMessageContent();
		SOAPMessageContent.Header header;
		TestSoapService.TestRequest req = new TestSoapService.TestRequest();
		req.name = "TestGuillaume";
		request.bodyContent = req;
		request.bodyNamespaceURI = "http://testRequest";
		request.bodyLocalName = "TestRequest";
		request.bodyType = new TypeDefinition(TestSoapService.TestRequest.class);
		
		header = new SOAPMessageContent.Header();
		header.localName = "LoginRequest";
		header.namespaceURI = "http://lecousin.net/framework/web/security";
		LoginRequest login = new LoginRequest();
		login.username = "guillaume";
		header.content = login;
		header.contentType = new TypeDefinition(LoginRequest.class);
		request.headers.add(header);

		TestSoapService.TestResult response = SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "helloWorldWithAuthentication", request, TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
		Assert.assertEquals("Hello TestGuillaume, you are well authenticated as guillaume", response.hello);
		
		request.headers.clear();
		try {
			SOAPClient.send(BASE_HTTP_URL + "/services/testSoap", "helloWorldWithAuthentication", request, TestSoapService.TestResult.class, "http://testResponse", new ArrayList<>(0)).blockResult(0);
			throw new AssertionError("Error expected when not sending login");
		} catch (HTTPResponseError e) {
			Assert.assertEquals(403, e.getStatusCode());
		}
	}
	
	@Test(timeout=120000)
	public void testWithSOAPUIFromWSDL() throws Exception {
		WsdlProject project = new WsdlProject();
		WsdlInterface iface = WsdlInterfaceFactory.importWsdl(project, BASE_HTTP_URL + "/services/testSoap/wsdl", true )[0];
		WsdlOperation operation = (WsdlOperation) iface.getOperationByName("helloWorld");
		WsdlRequest request = operation.addNewRequest("My request");
		request.setRequestContent(operation.createRequest(true));
		request.setEndpoint(BASE_HTTP_URL + "/services/testSoap");
		WsdlSubmit submit = (WsdlSubmit) request.submit(new WsdlSubmitContext(iface), false);
		Response response = submit.getResponse();
		String content = response.getContentAsString();
		if (content.contains("hello=\"Hello ?\"")) return;
		System.out.println(response.getContentAsString());
		throw new Exception("Invalid SOAP response");
	}
	
}
