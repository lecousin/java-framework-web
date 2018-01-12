package net.lecousin.framework.web.services.soap.filters;

import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.security.LoginRequest;
import net.lecousin.framework.web.security.TokenRequest;
import net.lecousin.framework.web.services.soap.SOAP;
import net.lecousin.framework.web.services.soap.SOAPFilter;

public class GetAuthenticationRequestFromSOAPHeader implements SOAPFilter {

	@PreFilter
	public void login(@SOAP.Header(localName="LoginRequest", namespaceURI="http://lecousin.net/framework/web/security", required=false) LoginRequest login, WebRequest request) {
		if (login != null && login.username != null && login.username.length() > 0)
			request.addAuthenticationRequest(login);
	}

	@PreFilter
	public void token(@SOAP.Header(localName="TokenRequest", namespaceURI="http://lecousin.net/framework/web/security", required=false) TokenRequest token, WebRequest request) {
		if (token != null && token.token != null && token.token.length() > 0)
			request.addAuthenticationRequest(token);
	}
	
}
