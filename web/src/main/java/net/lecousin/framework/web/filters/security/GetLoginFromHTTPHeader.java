package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.network.mime.MimeUtil;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.LoginRequest;

/**
 * Retrieve username and password from HTTP headers.
 * The headers to use can be specified in the fields <code>usernameHeader</code>
 * and <code>passwordHeader</code>.
 * If at least the username header is present, a {@link LoginRequest} is added in
 * the WebRequest and can be retrieved using the method {@link WebRequest#getAuthenticationRequests()}.
 */
public class GetLoginFromHTTPHeader implements WebRequestFilter {

	public String usernameHeader = "X-Username";
	public String passwordHeader = "X-Password";
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		String username = request.getRequest().getMIME().getFirstHeaderRawValue(usernameHeader);
		if (username == null)
			return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
		String password = request.getRequest().getMIME().getFirstHeaderRawValue(passwordHeader);
		if (password == null) password = "";
		try { username = MimeUtil.decodeRFC2047(username); }
		catch (Throwable t) { /* ignore */ }
		try { password = MimeUtil.decodeRFC2047(password); }
		catch (Throwable t) { /* ignore */ }
		request.addAuthenticationRequest(new LoginRequest(username, password));
		return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
	}
	
}
