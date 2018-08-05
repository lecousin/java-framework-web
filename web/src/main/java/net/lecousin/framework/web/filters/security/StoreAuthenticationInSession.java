package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.network.session.ISession;
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
		AsyncWork<IAuthentication, Exception> auth = request.authenticate(authenticationProvider);
		AsyncWork<FilterResult, Exception> result = new AsyncWork<>();
		if (auth.isUnblocked())
			store(auth.getResult(), request, result);
		else
			auth.listenAsync(new Task.Cpu.FromRunnable("Check authentication", Task.PRIORITY_NORMAL, () -> {
				store(auth.getResult(), request, result);
			}), true); 
		return result;
	}
	
	private void store(IAuthentication a, WebRequest request, AsyncWork<FilterResult, Exception> result) {
		if (a != null) {
			ISession session = request.getSession(true);
			if (session != null)
				session.putData(sessionParameter, a);
		}
		result.unblockSuccess(FilterResult.CONTINUE_PROCESSING);
	}
	
}
