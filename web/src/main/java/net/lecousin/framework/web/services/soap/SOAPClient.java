package net.lecousin.framework.web.services.soap;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.out2in.OutputToInputBuffers;
import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.io.serialization.rules.SerializationRule;
import net.lecousin.framework.network.http.HTTPRequest.Method;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.client.HTTPClientUtil;
import net.lecousin.framework.network.mime.MimeMessage;
import net.lecousin.framework.network.mime.header.ParameterizedHeaderValue;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.xml.XMLStreamEvents.ElementContext;
import net.lecousin.framework.xml.XMLStreamEvents.Event;
import net.lecousin.framework.xml.XMLStreamReaderAsync;
import net.lecousin.framework.xml.dom.XMLDocument;
import net.lecousin.framework.xml.dom.XMLElement;
import net.lecousin.framework.xml.serialization.XMLDeserializer;

import org.w3c.dom.Element;

public final class SOAPClient {

	public static SOAPMessageContent createMessage(Object request, String defaultNamespaceURI) throws Exception {
		if (request instanceof SOAPMessageContent)
			return (SOAPMessageContent)request;
		if (request == null)
			return new SOAPMessageContent();
		Class<?> c = request.getClass();
		if (c.getAnnotation(SOAP.Message.class) != null) {
			SOAPMessageContent message = new SOAPMessageContent();
			SOAPUtil.createContentFromMessage(request, message, defaultNamespaceURI);
			return message;
		}
		if (request instanceof Element) {
			SOAPMessageContent message = new SOAPMessageContent();
			message.bodyContent = request;
			return message;
		}
		SOAPMessageContent message = new SOAPMessageContent();
		message.bodyContent = request;
		message.bodyType = new TypeDefinition(request.getClass());
		message.bodyNamespaceURI = defaultNamespaceURI;
		message.bodyLocalName = request.getClass().getSimpleName();
		return message;
	}
	
	@SuppressWarnings("resource")
	public static MimeMessage createMimeMessage(SOAPMessageContent message, List<SerializationRule> rules) {
		OutputToInputBuffers o2i = new OutputToInputBuffers(false, Task.PRIORITY_NORMAL);
		SOAPUtil.sendMessage(message, o2i, rules);
		MimeMessage mime = new MimeMessage();
		mime.setHeaderRaw(MimeMessage.CONTENT_TYPE, "application/soap+xml; charset=utf-8");
		mime.setBodyToSend(o2i);
		// TODO check the HTTP client closes the body when sent or error
		return mime;
	}
	
	public static <T> AsyncWork<T, Exception> send(String url, String action, Object request, String namespaceURI, Class<T> responseType, List<SerializationRule> rules) {
		return send(url, action, request, namespaceURI, responseType, namespaceURI, rules);
	}
	
	public static <T> AsyncWork<Pair<T, HTTPResponse>, Exception> sendAndGetHTTPResponse(String url, String action, Object request, String namespaceURI, Class<T> responseType, List<SerializationRule> rules) {
		return sendAndGetHTTPResponse(url, action, request, namespaceURI, responseType, namespaceURI, rules);
	}
	
	public static <T> AsyncWork<T, Exception> send(String url, String action, Object request, String requestNamespaceURI, Class<T> responseType, String responseNamespaceURI, List<SerializationRule> rules) {
		try {
			return send(url, action, createMessage(request, requestNamespaceURI), responseType, responseNamespaceURI, rules);
		} catch (Exception e) {
			return new AsyncWork<>(null, e);
		}
	}
	
	public static <T> AsyncWork<Pair<T, HTTPResponse>, Exception> sendAndGetHTTPResponse(String url, String action, Object request, String requestNamespaceURI, Class<T> responseType, String responseNamespaceURI, List<SerializationRule> rules) {
		try {
			return sendAndGetHTTPResponse(url, action, createMessage(request, requestNamespaceURI), responseType, responseNamespaceURI, rules);
		} catch (Exception e) {
			return new AsyncWork<>(null, e);
		}
	}
	
	public static SynchronizationPoint<Exception> send(String url, String action, Object request, String defaultNamespaceURI, SOAPMessageContent responseToFill, List<SerializationRule> rules) {
		try {
			return send(url, action, createMessage(request, defaultNamespaceURI), responseToFill, rules);
		} catch (Exception e) {
			return new SynchronizationPoint<>(e);
		}
	}
	
	public static AsyncWork<HTTPResponse, Exception> sendAndGetHTTPResponse(String url, String action, Object request, String defaultNamespaceURI, SOAPMessageContent responseToFill, List<SerializationRule> rules) {
		try {
			return sendAndGetHTTPResponse(url, action, createMessage(request, defaultNamespaceURI), responseToFill, rules);
		} catch (Exception e) {
			return new AsyncWork<>(null, e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <T> AsyncWork<T, Exception> send(String url, String action, SOAPMessageContent message, Class<T> responseType, String responseNamespaceURI, List<SerializationRule> rules) {
		SOAPMessageContent responseToFill = new SOAPMessageContent();
		if (responseType != null) {
			responseToFill.bodyNamespaceURI = responseNamespaceURI;
			responseToFill.bodyLocalName = responseType.getSimpleName();
			responseToFill.bodyType = new TypeDefinition(responseType);
		}
		SynchronizationPoint<Exception> send = send(url, action, createMimeMessage(message, rules), responseToFill, rules);
		AsyncWork<T, Exception> result = new AsyncWork<>();
		send.listenInline(() -> {
			result.unblockSuccess((T)responseToFill.bodyContent);
		}, result);
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> AsyncWork<Pair<T, HTTPResponse>, Exception> sendAndGetHTTPResponse(String url, String action, SOAPMessageContent message, Class<T> responseType, String responseNamespaceURI, List<SerializationRule> rules) {
		SOAPMessageContent responseToFill = new SOAPMessageContent();
		if (responseType != null) {
			responseToFill.bodyNamespaceURI = responseNamespaceURI;
			responseToFill.bodyLocalName = responseType.getSimpleName();
			responseToFill.bodyType = new TypeDefinition(responseType);
		}
		AsyncWork<HTTPResponse, Exception> send = sendAndGetHTTPResponse(url, action, createMimeMessage(message, rules), responseToFill, rules);
		AsyncWork<Pair<T, HTTPResponse>, Exception> result = new AsyncWork<>();
		send.listenInline(() -> {
			result.unblockSuccess(new Pair<>((T)responseToFill.bodyContent, send.getResult()));
		}, result);
		return result;
	}
	
	public static SynchronizationPoint<Exception> send(String url, String action, SOAPMessageContent message, SOAPMessageContent responseToFill, List<SerializationRule> rules) {
		return send(url, action, createMimeMessage(message, rules), responseToFill, rules);
	}
	
	public static AsyncWork<HTTPResponse, Exception> sendAndGetHTTPResponse(String url, String action, SOAPMessageContent message, SOAPMessageContent responseToFill, List<SerializationRule> rules) {
		return sendAndGetHTTPResponse(url, action, createMimeMessage(message, rules), responseToFill, rules);
	}
	
	public static SynchronizationPoint<Exception> send(String url, String action, MimeMessage message, SOAPMessageContent responseToFill, List<SerializationRule> rules) {
		AsyncWork<Pair<HTTPResponse, IO.Readable.Seekable>, Exception> send = send(url, action, message);
		SynchronizationPoint<Exception> result = new SynchronizationPoint<>();
		send.listenAsync(new Task.Cpu.FromRunnable("Parsing SOAP response", Task.PRIORITY_NORMAL, () -> {
			parseResponse(send.getResult().getValue1(), send.getResult().getValue2(), responseToFill, rules, result);
		}), result);
		return result;
	}
	
	public static AsyncWork<HTTPResponse, Exception> sendAndGetHTTPResponse(String url, String action, MimeMessage message, SOAPMessageContent responseToFill, List<SerializationRule> rules) {
		AsyncWork<Pair<HTTPResponse, IO.Readable.Seekable>, Exception> send = send(url, action, message);
		AsyncWork<HTTPResponse, Exception> result = new AsyncWork<>();
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		send.listenAsync(new Task.Cpu.FromRunnable("Parsing SOAP response", Task.PRIORITY_NORMAL, () -> {
			parseResponse(send.getResult().getValue1(), send.getResult().getValue2(), responseToFill, rules, sp);
			sp.listenInline(() -> { result.unblockSuccess(send.getResult().getValue1()); }, result);
		}), result);
		return result;
	}
	
	public static AsyncWork<Pair<HTTPResponse, IO.Readable.Seekable>, Exception> send(String url, String action, MimeMessage message) {
		AsyncWork<Pair<HTTPResponse, IO.Readable.Seekable>, Exception> result = new AsyncWork<>();
		try {
			message.setHeaderRaw("SOAPAction", action);
			AsyncWork<Pair<HTTPResponse, IO.Readable.Seekable>, IOException> send = HTTPClientUtil.sendAndReceive(Method.POST, url, message);
			send.listenInlineSP(() -> { result.unblockSuccess(send.getResult()); }, result);
		} catch (Exception e) {
			result.error(e);
		}
		return result;
	}
	
	public static void parseResponse(HTTPResponse response, IO.Readable content, SOAPMessageContent toFill, List<SerializationRule> rules, SynchronizationPoint<Exception> result) {
		String encoding = null;
		ParameterizedHeaderValue contentType;
		try { contentType = response.getMIME().getContentType(); }
		catch (Exception e) {
			result.error(new Exception("Invalid content-type", e));
			return;
		}
		if (contentType != null) {
			encoding = contentType.getParameterIgnoreCase("charset");
		}
		XMLStreamReaderAsync xml = new XMLStreamReaderAsync(content, Charset.forName(encoding), 4096);
		ISynchronizationPoint<Exception> start = xml.startRootElement();
		start.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
			if (start.hasError()) {
				result.error(new Exception("Error parsing SOAP response", start.getError()));
				return;
			}
			if (!xml.event.localName.equals("Envelope") || !xml.getNamespaceURI(xml.event.namespacePrefix).equals(SOAPUtil.SOAP_NS)) {
				result.error(new Exception("Invalid SOAP response: Root element must be an Envelope in namespace " + SOAPUtil.SOAP_NS));
				return;
			}
			parseEnvelope(xml, toFill, rules, result);
		}), true);
	}
	
	private static void parseEnvelope(XMLStreamReaderAsync xml, SOAPMessageContent toFill, List<SerializationRule> rules, SynchronizationPoint<Exception> result) {
		ElementContext elem = xml.event.context.getFirst();
		ISynchronizationPoint<Exception> next = xml.nextInnerElement(elem);
		if (next.isUnblocked()) {
			if (next.hasError()) {
				result.error(new Exception("Error parsing SOAP response", next.getError()));
				return;
			}
			if (xml.event.localName.equals("Header")) {
				if (xml.event.isClosed) {
					goToBody(xml, toFill, rules, result);
					return;
				}
				parseHeader(xml, toFill, rules, result);
				return;
			}
			if (xml.event.localName.equals("Body")) {
				parseBody(xml, toFill, rules, result);
				return;
			}
			result.error(new Exception("Error parsing SOAP response: Unexpected element " + xml.event.localName + ", Header or Body expected"));
			return;
		}
		next.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
			if (next.hasError()) {
				result.error(new Exception("Error parsing SOAP response", next.getError()));
				return;
			}
			if (xml.event.localName.equals("Header")) {
				if (xml.event.isClosed) {
					goToBody(xml, toFill, rules, result);
					return;
				}
				parseHeader(xml, toFill, rules, result);
				return;
			}
			if (xml.event.localName.equals("Body")) {
				parseBody(xml, toFill, rules, result);
				return;
			}
			result.error(new Exception("Error parsing SOAP response: Unexpected element " + xml.event.localName + ", Header or Body expected"));
		}), true);
	}
	
	private static void parseHeader(XMLStreamReaderAsync xml, SOAPMessageContent toFill, List<SerializationRule> rules, SynchronizationPoint<Exception> result) {
		do {
			ISynchronizationPoint<Exception> next = xml.next();
			if (next.isUnblocked()) {
				if (next.hasError()) {
					result.error(new Exception("Error parsing SOAP response", next.getError()));
					return;
				}
				if (Event.Type.END_ELEMENT.equals(xml.event.type)) {
					goToBody(xml, toFill, rules, result);
					return;
				}
				if (Event.Type.START_ELEMENT.equals(xml.event.type)) {
					for (SOAPMessageContent.Header h : toFill.headers) {
						if (h.namespaceURI != null && !xml.event.namespaceURI.equals(h.namespaceURI))
							continue;
						if (!xml.event.localName.equals(h.localName))
							continue;
						fillHeader(xml, h, toFill, rules, result);
						return;
					}
					ISynchronizationPoint<Exception> close = xml.closeElement();
					if (close.isUnblocked()) {
						if (close.hasError()) {
							result.error(new Exception("Error parsing SOAP response", close.getError()));
							return;
						}
						continue;
					}
					close.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
						if (close.hasError()) {
							result.error(new Exception("Error parsing SOAP response", close.getError()));
							return;
						}
						parseHeader(xml, toFill, rules, result);
					}), true);
					return;
				}
				continue;
			}
			next.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
				if (next.hasError()) {
					result.error(new Exception("Error parsing SOAP response", next.getError()));
					return;
				}
				if (Event.Type.END_ELEMENT.equals(xml.event.type)) {
					goToBody(xml, toFill, rules, result);
					return;
				}
				if (Event.Type.START_ELEMENT.equals(xml.event.type)) {
					for (SOAPMessageContent.Header h : toFill.headers) {
						if (h.namespaceURI != null && !xml.event.namespaceURI.equals(h.namespaceURI))
							continue;
						if (!xml.event.localName.equals(h.localName))
							continue;
						fillHeader(xml, h, toFill, rules, result);
						return;
					}
					ISynchronizationPoint<Exception> close = xml.closeElement();
					if (close.isUnblocked()) {
						if (close.hasError()) {
							result.error(new Exception("Error parsing SOAP response", close.getError()));
							return;
						}
						parseHeader(xml, toFill, rules, result);
						return;
					}
					close.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
						if (close.hasError()) {
							result.error(new Exception("Error parsing SOAP response", close.getError()));
							return;
						}
						parseHeader(xml, toFill, rules, result);
					}), true);
					return;
				}
				parseHeader(xml, toFill, rules, result);
			}), true);
			return;
		} while (true);
	}
	
	private static void fillHeader(XMLStreamReaderAsync xml, SOAPMessageContent.Header header, SOAPMessageContent toFill, List<SerializationRule> rules, SynchronizationPoint<Exception> result) {
		AsyncWork<?, Exception> fill = 
			new XMLDeserializer(xml, header.namespaceURI, header.localName)
				.deserializeValue(null, header.contentType, "", rules);
		fill.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
			if (fill.hasError()) {
				result.error(new Exception("Error deserializing header", fill.getError()));
				return;
			}
			header.content = fill.getResult();
			parseHeader(xml, toFill, rules, result);
		}), true);
	}
	
	private static void goToBody(XMLStreamReaderAsync xml, SOAPMessageContent toFill, List<SerializationRule> rules, SynchronizationPoint<Exception> result) {
		ISynchronizationPoint<Exception> next = xml.nextStartElement();
		if (next.isUnblocked()) {
			if (next.hasError()) {
				result.error(new Exception("Error parsing SOAP response", next.getError()));
				return;
			}
			if (!xml.event.localName.equals("Body")) {
				result.error(new Exception("Error parsing SOAP response: Unexpected element " + xml.event.localName + ", Body expected"));
				return;
			}
			parseBody(xml, toFill, rules, result);
			return;
		}
		next.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
			if (next.hasError()) {
				result.error(new Exception("Error parsing SOAP response", next.getError()));
				return;
			}
			if (!xml.event.localName.equals("Body")) {
				result.error(new Exception("Error parsing SOAP response: Unexpected element " + xml.event.localName + ", Body expected"));
				return;
			}
			parseBody(xml, toFill, rules, result);
		}), true);

	}
	
	private static void parseBody(XMLStreamReaderAsync xml, SOAPMessageContent toFill, List<SerializationRule> rules, SynchronizationPoint<Exception> result) {
		ElementContext bodyContext = xml.event.context.getFirst();
		AsyncWork<Boolean, Exception> bodyContent = xml.nextInnerElement(bodyContext);
		if (bodyContent.isUnblocked()) {
			if (bodyContent.hasError()) {
				result.error(new Exception("Error parsing SOAP response", bodyContent.getError()));
				return;
			}
			if (!bodyContent.getResult().booleanValue()) {
				if (toFill.bodyType == null) {
					result.unblock();
					return;
				}
				result.error(new Exception("No response message in SOAP envelope body"));
				return;
			}
			fillBody(xml, toFill, rules, result);
			return;
		}
		bodyContent.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
			if (bodyContent.hasError()) {
				result.error(new Exception("Error parsing SOAP response", bodyContent.getError()));
				return;
			}
			if (!bodyContent.getResult().booleanValue()) {
				if (toFill.bodyType == null) {
					result.unblock();
					return;
				}
				result.error(new Exception("No response message in SOAP envelope body"));
				return;
			}
			fillBody(xml, toFill, rules, result);
		}), true);
	}
	
	private static void fillBody(XMLStreamReaderAsync xml, SOAPMessageContent toFill, List<SerializationRule> rules, SynchronizationPoint<Exception> result) {
		if (toFill.bodyType.getBase().equals(Element.class)) {
			XMLDocument doc = new XMLDocument();
			AsyncWork<XMLElement, Exception> create = XMLElement.create(doc, xml);
			create.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
				if (create.hasError()) {
					result.error(new Exception("Error parsing SOAP response body", create.getError()));
					return;
				}
				toFill.bodyContent = create.getResult();
				result.unblock();
			}), true);
			return;
		}
		AsyncWork<?, Exception> fill = 
			new XMLDeserializer(xml, toFill.bodyNamespaceURI, toFill.bodyLocalName)
				.deserializeValue(null, toFill.bodyType, "", rules);
		fill.listenAsync(new Task.Cpu.FromRunnable("Parse SOAP response", Task.PRIORITY_NORMAL, () -> {
			if (fill.hasError()) {
				result.error(new Exception("Error deserializing body", fill.getError()));
				return;
			}
			toFill.bodyContent = fill.getResult();
			result.unblock();
		}), true);
	}
}
