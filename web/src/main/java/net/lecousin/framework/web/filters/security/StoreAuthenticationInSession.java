package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
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
		if (auth.isUnblocked()) {
			IAuthentication a = auth.getResult();
			if (a != null) {
				ISession session = request.getSession(true);
				if (session != null)
					session.putData(sessionParameter, a);
			}
			result.unblockSuccess(FilterResult.CONTINUE_PROCESSING);
			return result;
		}
		auth.listenAsync(new Task.Cpu<Void, NoException>("Check authentication", Task.PRIORITY_NORMAL) {
			@Override
			public Void run() {
				IAuthentication a = auth.getResult();
				if (a != null) {
					ISession session = request.getSession(true);
					if (session != null)
						session.putData(sessionParameter, a);
				}
				result.unblockSuccess(FilterResult.CONTINUE_PROCESSING);
				return null;
			}
		}, true);
		return result;
	}
	
}
