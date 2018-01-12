package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.LoginRequest;

/**
 * Retrieve username and password from request parameters.
 * The parameters to use can be specified in the fields <code>usernameParameter</code>
 * and <code>passwordParameter</code>.
 * If at least the username is present, a {@link LoginRequest} is added in
 * the WebRequest and can be retrieved using the method {@link WebRequest#getAuthenticationRequests()}.
 */
public class GetLoginFromQuery implements WebRequestFilter {

	public String usernameParameter = "username";
	public String passwordParameter = "password";
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		String username = request.getRequest().getParameter(usernameParameter);
		if (username == null)
			return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
		String password = request.getRequest().getParameter(passwordParameter);
		if (password == null) password = "";
		request.addAuthenticationRequest(new LoginRequest(username, password));
		return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
	}
	
}
