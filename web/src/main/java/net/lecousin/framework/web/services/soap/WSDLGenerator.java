package net.lecousin.framework.web.services.soap;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.io.buffering.MemoryIO;
import net.lecousin.framework.util.ClassUtil;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.soap.SOAPWebServiceProvider.Operation;
import net.lecousin.framework.xml.XMLWriter;
import net.lecousin.framework.xml.serialization.XMLSpecWriter;

final class WSDLGenerator {
	
	@SuppressWarnings("resource")
	static ISynchronizationPoint<Exception> generateWSDL(SOAPWebServiceProvider provider, WebRequest request) {
		StringBuilder message = new StringBuilder(4096);
		StringBuilder portType = new StringBuilder(1024);
		StringBuilder binding = new StringBuilder(1024);
		MemoryIO xsdIO = new MemoryIO(2048, "SOAP XSD");

		String tns = XMLWriter.escape(provider.serviceDeclaration.targetNamespace());
		Map<String, String> additionalNamespaces = new HashMap<>();
		
		portType.append("<wsdl:portType name=\"").append(provider.getWebService().getClass().getSimpleName()).append("PortType\">\r\n");
		binding.append("<wsdl:binding type=\"tns:").append(provider.getWebService().getClass().getSimpleName()).append("PortType\" name=\"").append(provider.getWebService().getClass().getSimpleName()).append("Binding\">\r\n");
		binding.append("\t<soap:binding style=\"document\" transport=\"http://schemas.xmlsoap.org/soap/http\"/>\r\n");
		
		for (Operation o : provider.operations.values())
			generateWSDL(provider, o, tns, xsdIO, message, portType, binding, additionalNamespaces);
		
		portType.append("</wsdl:portType>\r\n");
		binding.append("</wsdl:binding>\r\n");
		
		StringBuilder wsdl = new StringBuilder(4096);
		wsdl.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		wsdl.append("<wsdl:definitions")
			.append(" targetNamespace=\"").append(tns).append("\"")
			.append(" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"")
			.append(" xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\"")
			.append(" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"")
			.append(" xmlns:tns=\"").append(tns).append("\"")
			.append(" xmlns:request=\"").append(tns).append("Request\"")
			.append(" xmlns:response=\"").append(tns).append("Response\"");
		for (Map.Entry<String, String> ns : additionalNamespaces.entrySet())
			wsdl.append(" xmlns:").append(ns.getValue()).append("=\"").append(ns.getKey()).append("\"");
		wsdl.append(">\r\n");
		wsdl.append("<wsdl:types>\r\n");
		try {
			xsdIO.seekSync(SeekType.FROM_BEGINNING, 0);
			IOUtil.readFullyAsStringSync(xsdIO, StandardCharsets.UTF_8, wsdl);
		} catch (IOException e) {
			return new SynchronizationPoint<>(e);
		}
		xsdIO.closeAsync();
		xsdIO = null;
		wsdl.append("</wsdl:types>\r\n");
		wsdl.append(message).append("\r\n");
		wsdl.append(portType).append("\r\n");
		wsdl.append(binding).append("\r\n");
		wsdl.append("<wsdl:service name=\"").append(provider.getWebService().getClass().getSimpleName()).append("\">");
		wsdl.append("\t<wsdl:port name=\"").append(provider.getWebService().getClass().getSimpleName()).append("Port")
			.append("\" binding=\"").append(provider.getWebService().getClass().getSimpleName()).append("Binding\">\r\n");
		wsdl.append("\t\t<soap:address location=\"");
		wsdl.append(request.getRootURL());
		wsdl.append(request.getCurrentPath());
		wsdl.append("\"/>\r\n");
		wsdl.append("\t</wsdl:port>\r\n");
		wsdl.append("</wsdl:service>");
		wsdl.append("</wsdl:definitions>");
		request.getResponse().setStatus(200);
		request.getResponse().setRawContentType("text/xml; charset=utf-8");
		request.getResponse().getMIME().setBodyToSend(new ByteArrayIO(wsdl.toString().getBytes(StandardCharsets.UTF_8), "wsdl"));
		return new SynchronizationPoint<>(true);
	}
	
	private static void generateWSDL(SOAPWebServiceProvider provider, Operation op, String tns, IO.Writable xsdIO, StringBuilder message, StringBuilder portType, StringBuilder binding, Map<String, String> additionalNamespaces) {
		Class<?> inputType = null;
		boolean inputAny = false;
		List<Pair<SOAP.Header, Class<?>>> inputHeaders = new LinkedList<>();
		java.lang.reflect.Parameter[] paramsDef = op.method.getParameters();
		for (int i = 0; i < paramsDef.length; ++i) {
			WebService.Body b = paramsDef[i].getAnnotation(WebService.Body.class);
			if (b != null) {
				inputType = paramsDef[i].getType();
				continue;
			}
			SOAP.BodyElement be = paramsDef[i].getAnnotation(SOAP.BodyElement.class);
			if (be != null) {
				inputAny = true;
				continue;
			}
			SOAP.Header h = paramsDef[i].getAnnotation(SOAP.Header.class);
			if (h != null) {
				inputHeaders.add(new Pair<>(h, paramsDef[i].getType()));
				continue;
			}
			SOAP.Message msg = paramsDef[i].getType().getAnnotation(SOAP.Message.class);
			if (msg != null) {
				for (Field f : ClassUtil.getAllFields(paramsDef[i].getType())) {
					if (f.getAnnotation(SOAP.MessageBody.class) != null) {
						inputType = f.getType();
						continue;
					}
					h = f.getAnnotation(SOAP.Header.class);
					if (h != null) {
						inputHeaders.add(new Pair<>(h, f.getType()));
						continue;
					}
				}
			}
		}
		String inputTypeName = inputType != null ? inputType.getSimpleName() : op.method.getName() + "Request";
		Map<String, String> namespaces = new HashMap<>(5);
		namespaces.put(tns + "Request", "request");
		XMLSpecWriter xsd = new XMLSpecWriter(tns + "Request", inputTypeName, namespaces, StandardCharsets.UTF_8, false);
		xsd.writeSpecification(inputAny ? null : inputType, xsdIO, provider.getParent().getSerializationRules()).block(0);
		
		for (Pair<SOAP.Header, Class<?>> header : inputHeaders) {
			namespaces = new HashMap<>(5);
			String namespaceURI = header.getValue1().namespaceURI();
			if (namespaceURI.isEmpty()) namespaceURI = tns;
			else if (!namespaceURI.equals(tns)) {
				if (!additionalNamespaces.containsKey(namespaceURI))
					additionalNamespaces.put(namespaceURI, "ns" + (additionalNamespaces.size() + 1));
			}
			xsd = new XMLSpecWriter(namespaceURI, header.getValue1().localName(), namespaces, StandardCharsets.UTF_8, false);
			xsd.writeSpecification(header.getValue2(), xsdIO, provider.getParent().getSerializationRules()).block(0);
		}
		
		Class<?> outputType = op.method.getReturnType();
		boolean outputAny = op.method.getAnnotatedReturnType().getAnnotation(SOAP.BodyElement.class) != null;
		if (AsyncWork.class.equals(outputType)) {
			Type t = op.method.getGenericReturnType();
			Type[] types = ((ParameterizedType)t).getActualTypeArguments();
			outputType = (Class<?>)types[0];
		}
		List<Pair<SOAP.Header, Class<?>>> outputHeaders = new LinkedList<>();
		if (outputType.getAnnotation(SOAP.Message.class) != null) {
			for (Field f : ClassUtil.getAllFields(outputType)) {
				if (f.getAnnotation(SOAP.MessageBody.class) != null) {
					outputType = f.getType();
					continue;
				}
				SOAP.Header h = f.getAnnotation(SOAP.Header.class);
				if (h != null) {
					outputHeaders.add(new Pair<>(h, f.getType()));
					continue;
				}
			}
		}
		String outputTypeName = outputAny ? op.method.getName() + "Response" : outputType.getSimpleName();
		namespaces = new HashMap<>(5);
		namespaces.put(tns + "Response", "response");
		xsd = new XMLSpecWriter(tns + "Response", outputTypeName, namespaces, StandardCharsets.UTF_8, false);
		xsd.writeSpecification(outputAny ? null : outputType, xsdIO, provider.getParent().getSerializationRules()).block(0);

		for (Pair<SOAP.Header, Class<?>> header : outputHeaders) {
			namespaces = new HashMap<>(5);
			String namespaceURI = header.getValue1().namespaceURI();
			if (namespaceURI.isEmpty()) namespaceURI = tns;
			else if (!namespaceURI.equals(tns)) {
				if (!additionalNamespaces.containsKey(namespaceURI))
					additionalNamespaces.put(namespaceURI, "ns" + (additionalNamespaces.size() + 1));
			}
			xsd = new XMLSpecWriter(namespaceURI, header.getValue1().localName(), namespaces, StandardCharsets.UTF_8, false);
			xsd.writeSpecification(header.getValue2(), xsdIO, provider.getParent().getSerializationRules()).block(0);
		}
		
		message.append("<wsdl:message name=\"").append(op.action).append("Request\">\r\n");
		int headerCount = 1;
		for (Pair<SOAP.Header, Class<?>> header : inputHeaders) {
			message.append("\t<wsdl:part name=\"header").append(headerCount++).append("\" element=\"");
			String namespaceURI = header.getValue1().namespaceURI();
			String prefix;
			if (namespaceURI.isEmpty()) prefix = "tns";
			else if (namespaceURI.equals(tns)) prefix = "tns";
			else prefix = additionalNamespaces.get(namespaceURI);
			message.append(prefix).append(':').append(header.getValue1().localName());
			message.append("\"/>\r\n");
		}
		message.append("\t<wsdl:part name=\"body\" element=\"request:").append(inputTypeName).append("\"/>\r\n");
		message.append("</wsdl:message>\r\n");
		message.append("<wsdl:message name=\"").append(op.action).append("Response\">\r\n");
		headerCount = 1;
		for (Pair<SOAP.Header, Class<?>> header : outputHeaders) {
			message.append("\t<wsdl:part name=\"header").append(headerCount++).append("\" element=\"");
			String namespaceURI = header.getValue1().namespaceURI();
			String prefix;
			if (namespaceURI.isEmpty()) prefix = "tns";
			else if (namespaceURI.equals(tns)) prefix = "tns";
			else prefix = additionalNamespaces.get(namespaceURI);
			message.append(prefix).append(':').append(header.getValue1().localName());
			message.append("\"/>\r\n");
		}
		message.append("\t<wsdl:part name=\"body\" element=\"response:").append(outputTypeName).append("\"/>\r\n");
		message.append("</wsdl:message>\r\n");
		
		portType.append("\t<wsdl:operation name=\"").append(op.action).append("\">\r\n");
		portType.append("\t\t<wsdl:input message=\"tns:").append(op.action).append("Request\"/>\r\n");
		portType.append("\t\t<wsdl:output message=\"tns:").append(op.action).append("Response\"/>\r\n");
		portType.append("\t</wsdl:operation>");
		
		binding.append("\t<wsdl:operation name=\"").append(op.action).append("\">\r\n");
		binding.append("\t\t<soap:operation soapAction=\"").append(op.action).append("\"/>\r\n");
		binding.append("\t\t<wsdl:input>\r\n");
		for (headerCount = 1; headerCount <= inputHeaders.size(); headerCount++)
			binding.append("\t\t\t<soap:header part=\"header").append(headerCount).append("\" use=\"literal\"/>\r\n");
		binding.append("\t\t\t<soap:body parts=\"body\" use=\"literal\"/>\r\n");
		binding.append("\t\t</wsdl:input>\r\n");
		binding.append("\t\t<wsdl:output>");
		for (headerCount = 1; headerCount <= outputHeaders.size(); headerCount++)
			binding.append("\t\t\t<soap:header part=\"header").append(headerCount).append("\" use=\"literal\"/>\r\n");
		binding.append("\t\t\t<soap:body parts=\"body\" use=\"literal\"/>\r\n");
		binding.append("\t\t</wsdl:output>\r\n");
		binding.append("\t</wsdl:operation>\r\n");
	}


}
