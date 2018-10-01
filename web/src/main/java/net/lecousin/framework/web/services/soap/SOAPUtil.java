package net.lecousin.framework.web.services.soap;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.OutputToInput;
import net.lecousin.framework.io.buffering.SimpleBufferedWritable;
import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.io.serialization.rules.SerializationRule;
import net.lecousin.framework.util.ClassUtil;
import net.lecousin.framework.xml.XMLWriter;
import net.lecousin.framework.xml.serialization.XMLSerializer;

import org.w3c.dom.Element;

public final class SOAPUtil {

	public static void createContentFromMessage(Object object, SOAPMessageContent message, String defaultHeaderNamespaceURI) throws Exception {
		for (Field f : ClassUtil.getAllFields(object.getClass())) {
			if (f.getAnnotation(SOAP.MessageBody.class) != null) {
				try { message.bodyContent = f.get(object); }
				catch (Throwable t) {
					throw new Exception("Error getting SOAP message body from field " + f.getName() + " of class " + object.getClass().getName(), t);
				}
				message.bodyType = new TypeDefinition(new TypeDefinition(object.getClass()), f.getGenericType());
				message.bodyLocalName = f.getType().getSimpleName();
				continue;
			}
			SOAP.Header h = f.getAnnotation(SOAP.Header.class);
			if (h != null) {
				SOAPMessageContent.Header header = new SOAPMessageContent.Header();
				try { header.content = f.get(object); }
				catch (Throwable t) {
					throw new Exception("Error getting SOAP message header from field " + f.getName() + " of class " + object.getClass().getName(), t);
				}
				if (header.content == null)
					continue;
				header.namespaceURI = h.namespaceURI();
				if (header.namespaceURI.length() == 0) header.namespaceURI = defaultHeaderNamespaceURI;
				header.localName = h.localName();
				header.contentType = new TypeDefinition(new TypeDefinition(object.getClass()), f.getGenericType());
				message.headers.add(header);
				continue;
			}
		}
	}
	
	public static void sendMessage(SOAPMessageContent message, OutputToInput output, List<SerializationRule> rules) {
		@SuppressWarnings("resource")
		SimpleBufferedWritable bout = new SimpleBufferedWritable(output, 4096);
		XMLWriter writer = new XMLWriter(bout, StandardCharsets.UTF_8, true, false);
		Map<String, String> namespaces = new HashMap<>();
		if (message.bodyNamespaceURI != null)
			namespaces.put(message.bodyNamespaceURI, "message");
		openEnvelope(writer, namespaces);
		if (message.headers.isEmpty()) {
			sendBody(false, message, output, bout, writer, rules);
		} else {
			openHeader(writer);
			sendHeader(message, 0, output, bout, writer, rules);
		}
	}
	
	private static void sendHeader(SOAPMessageContent message, int headerIndex, OutputToInput out, SimpleBufferedWritable bout, XMLWriter writer, List<SerializationRule> rules) {
		if (headerIndex == message.headers.size()) {
			sendBody(true, message, out, bout, writer, rules);
			return;
		}
		SOAPMessageContent.Header header = message.headers.get(headerIndex);
		HashMap<String, String> namespaces = new HashMap<>();
		if (header.namespaceURI != null)
			namespaces.put(header.namespaceURI, "header" + (headerIndex + 1));
		writer.openElement(header.namespaceURI, header.localName, namespaces);
		ISynchronizationPoint<? extends Exception> write = new XMLSerializer(writer).serializeValue(null, header.content, header.contentType, "", rules);
		if (write.isUnblocked()) {
			if (write.hasError()) {
				out.signalErrorBeforeEndOfData(IO.error(write.getError()));
				return;
			}
			writer.closeElement();
			sendHeader(message, headerIndex + 1, out, bout, writer, rules);
			return;
		}
		write.listenAsync(new Task.Cpu.FromRunnable("Send SOAP message", Task.PRIORITY_NORMAL, () -> {
			if (write.hasError()) {
				out.signalErrorBeforeEndOfData(IO.error(write.getError()));
				return;
			}
			writer.closeElement();
			sendHeader(message, headerIndex + 1, out, bout, writer, rules);
		}), true);
	}
	
	private static void sendBody(boolean closeHeader, SOAPMessageContent message, OutputToInput out, SimpleBufferedWritable bout, XMLWriter writer, List<SerializationRule> rules) {
		if (closeHeader)
			writer.closeElement();
		openBody(writer);
		ISynchronizationPoint<? extends Exception> write;
		if (message.bodyContent == null)
			write = writer.addAttribute("xsi:nil", "true");
		else if (message.bodyContent instanceof Element)
			write = writer.write((Element)message.bodyContent);
		else {
			writer.openElement(message.bodyNamespaceURI, message.bodyLocalName, null);
			write = new XMLSerializer(writer).serializeValue(null, message.bodyContent, message.bodyType, "", rules);
		}
		if (write.isUnblocked()) {
			if (write.hasError()) {
				out.signalErrorBeforeEndOfData(IO.error(write.getError()));
				return;
			}
			finalizeSending(out, bout, writer);
			return;
		}
		write.listenAsync(new Task.Cpu.FromRunnable("Finalize sending SOAP message", Task.PRIORITY_NORMAL, () -> {
			if (write.hasError()) {
				out.signalErrorBeforeEndOfData(IO.error(write.getError()));
				return;
			}
			finalizeSending(out, bout, writer);
		}), true);
	}

	public static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
	
	public static void openEnvelope(XMLWriter writer, Map<String, String> namespaces) {
		if (namespaces == null) namespaces = new HashMap<>(5);
		if (!namespaces.containsKey(SOAP_NS))
			namespaces.put(SOAP_NS, "soap");
		writer.start(SOAP_NS, "Envelope", namespaces);
	}
	
	public static void openHeader(XMLWriter writer) {
		writer.openElement(SOAP_NS, "Header", null);
	}
	
	public static void openBody(XMLWriter writer) {
		writer.openElement(SOAP_NS, "Body", null);
	}
	
	public static void finalizeSending(OutputToInput out, SimpleBufferedWritable bout, XMLWriter writer) {
		writer.end().listenInline(() -> {
			bout.flush().listenInline(() -> {
				out.endOfData();
			}, (error) -> {
				out.signalErrorBeforeEndOfData(error);
			}, (cancel) -> {});
		}, (error) -> {
			out.signalErrorBeforeEndOfData(error);
		}, (cancel) -> {});
	}

}
