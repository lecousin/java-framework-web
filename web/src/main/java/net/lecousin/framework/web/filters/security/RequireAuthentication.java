package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;

/**
 * Reject any request without authenticated user.
 */
public class RequireAuthentication implements WebRequestFilter {

	/** Constructor. */
	public RequireAuthentication() {
	}

	@Inject
	private IAuthenticationProvider authenticationProvider;
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		AsyncWork<IAuthentication, Exception> auth = request.authenticate(authenticationProvider);
		AsyncWork<FilterResult, Exception> result = new AsyncWork<>();
		auth.listenAsync(new Task.Cpu<Void, NoException>("Check authentication", Task.PRIORITY_NORMAL) {
			@Override
			public Void run() {
				if (auth.hasError()) {
					request.getResponse().setStatus(403, "Unable to authenticate: " + auth.getError().getMessage());
					result.unblockSuccess(FilterResult.STOP_PROCESSING);
					return null;
				}
				IAuthentication a = auth.getResult();
				if (a == null) {
					// not authenticated
					request.getResponse().setStatus(401, "You must be authenticated for this request");
					result.unblockSuccess(FilterResult.STOP_PROCESSING);
					return null;
				}
				result.unblockSuccess(FilterResult.CONTINUE_PROCESSING);
				return null;
			}
		}, true);
		return result;
	}
}
