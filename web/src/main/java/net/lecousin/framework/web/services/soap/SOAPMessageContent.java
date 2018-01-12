package net.lecousin.framework.web.services.soap;

import java.util.LinkedList;
import java.util.List;

import net.lecousin.framework.io.serialization.TypeDefinition;

/** This class is used to hold the content of a SOAP message before to send it to the client or the server.
 * It is also the structure passed to post filters, which may modify it or add headers.
 */
public class SOAPMessageContent {

	/** Header to send.
	 * If the content is a DOM element, it is directly written without serialization.
	 */
	public static class Header {
		
		public String namespaceURI;
		public String localName;
		public Object content;
		public TypeDefinition contentType;
		
	}
	
	public List<Header> headers = new LinkedList<>();
	
	public String bodyNamespaceURI;
	public String bodyLocalName;
	public Object bodyContent;
	public TypeDefinition bodyType;
	
}
