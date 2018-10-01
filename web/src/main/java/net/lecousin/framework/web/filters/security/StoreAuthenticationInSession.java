package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;

/**
 * If an IAuthentication exists for the specified IAuthenticationProvider, it is stored in the session with
 * the specified parameter name; else an authentication is attempted and the result is stored in the session on success.
 */
public class StoreAuthenticationInSession implements WebRequestFilter {

	@Inject
	public IAuthenticationProvider authenticationProvider;
	public String sessionParameter;
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		AsyncWork<FilterResult, Exception> result = new AsyncWork<>();
		IAuthentication auth = request.getAuthentication(authenticationProvider);
		if (auth != null) {
			request.getSession(true).listenInline((session) -> {
				if (session != null)
					session.putData(sessionParameter, auth);
				result.unblockSuccess(FilterResult.CONTINUE_PROCESSING);
			});
		} else {
			request.getSession(false).listenInline((session) -> {
				if (session != null)
					session.removeData(sessionParameter);
				result.unblockSuccess(FilterResult.CONTINUE_PROCESSING);
			});
		}
		return result;
	}
	
}
