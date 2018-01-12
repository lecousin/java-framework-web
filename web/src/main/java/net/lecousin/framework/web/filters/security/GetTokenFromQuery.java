package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.TokenRequest;

/**
* Retrieve an authentication token from request parameters.
* The parameter to use can be specified in the field <code>tokenParameter</code>.
* A token type should also be specified in the field <code>tokenType</code> to attach
* to the token.
* If the token is present, a {@link TokenRequest} is added in
* the WebRequest and can be retrieved using the method {@link WebRequest#getAuthenticationRequests()}.
*/
public class GetTokenFromQuery implements WebRequestFilter {

	public String tokenType = "";
	public String tokenParameter = "token";
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		String token = request.getRequest().getParameter(tokenParameter);
		if (token == null)
			return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
		request.addAuthenticationRequest(new TokenRequest(tokenType, token));
		return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
	}
	
}
