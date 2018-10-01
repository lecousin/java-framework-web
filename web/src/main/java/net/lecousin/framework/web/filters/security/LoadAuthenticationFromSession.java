package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;

/**
 * Load an IAuthentication from the session if it exists.
 * Typically this is used together with the StoreAuthenticationInSession filter.
 */
public class LoadAuthenticationFromSession implements WebRequestFilter {

	@Inject
	public IAuthenticationProvider authenticationProvider;
	public String sessionParameter;
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		AsyncWork<FilterResult, Exception> result = new AsyncWork<>();
		request.getSession(false).listenInline((session) -> {
			if (session != null) {
				Object auth = session.getData(sessionParameter);
				if (auth != null && (auth instanceof IAuthentication))
					request.addAuthentication(authenticationProvider, (IAuthentication)auth);
			}
			result.unblockSuccess(FilterResult.CONTINUE_PROCESSING);
		});
		return result;
	}

}
