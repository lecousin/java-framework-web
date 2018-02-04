package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.network.mime.MimeUtil;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.TokenRequest;

/**
* Retrieve an authentication token from HTTP headers.
* The header to use can be specified in the field <code>tokenHeader</code>.
* A token type should also be specified in the field <code>tokenType</code> to attach
* to the token.
* If the token is present, a {@link TokenRequest} is added in
* the WebRequest and can be retrieved using the method {@link WebRequest#getAuthenticationRequests()}.
*/
public class GetTokenFromHTTPHeader implements WebRequestFilter {

	public String tokenType = "";
	public String tokenHeader = "token";
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		String token = request.getRequest().getMIME().getFirstHeaderRawValue(tokenHeader);
		if (token == null)
			return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
		try { token = MimeUtil.decodeRFC2047(token); }
		catch (Throwable t) { /* ignore */ }
		request.addAuthenticationRequest(new TokenRequest(tokenType, token));
		return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
	}
	
}
