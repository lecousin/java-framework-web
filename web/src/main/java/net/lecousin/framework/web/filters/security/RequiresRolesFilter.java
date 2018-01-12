package net.lecousin.framework.web.filters.security;

import java.util.List;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;

/**
 * Reject any request without authenticated user, or with a user who does not have the specified roles.
 */
public class RequiresRolesFilter implements WebRequestFilter {

	/** Constructor. */
	public RequiresRolesFilter(List<String> roles) {
		this.roles = roles;
	}

	/** Constructor. */
	public RequiresRolesFilter() {
		this(null);
	}
	
	@Inject
	private IAuthenticationProvider authenticationProvider;
	
	private List<String> roles;
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		AsyncWork<IAuthentication, Exception> auth = request.authenticate(authenticationProvider);
		AsyncWork<FilterResult, Exception> result = new AsyncWork<>();
		auth.listenAsync(new Task.Cpu<Void, NoException>("Check user roles", Task.PRIORITY_NORMAL) {
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
					request.getResponse().setStatus(403, "You must be authenticated for this request");
					result.unblockSuccess(FilterResult.STOP_PROCESSING);
					return null;
				}
				for (String r : roles)
					if (!a.hasRole(r)) {
						request.getResponse().setStatus(403, "Request not allowed, it needs role " + r);
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
