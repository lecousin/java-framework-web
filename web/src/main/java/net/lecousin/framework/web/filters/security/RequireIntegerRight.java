package net.lecousin.framework.web.filters.security;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.IRightsManager;

/**
 * Reject any request without authenticated user, or with a user who does not have the specified right.
 */
public class RequireIntegerRight implements WebRequestFilter {

	/** Constructor. */
	public RequireIntegerRight(String rightName, FragmentedRangeLong rightValues) {
		this.rightName = rightName;
		this.rightValues = rightValues;
	}

	/** Constructor. */
	public RequireIntegerRight() {
		this(null, new FragmentedRangeLong());
	}
	
	@Inject
	private IAuthenticationProvider authenticationProvider;
	@Inject
	private IRightsManager rightsManager;
	
	private String rightName;
	private FragmentedRangeLong rightValues;
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		AsyncWork<IAuthentication, Exception> auth = request.authenticate(authenticationProvider);
		AsyncWork<FilterResult, Exception> result = new AsyncWork<>();
		auth.listenAsync(new Task.Cpu<Void, NoException>("Check user rights", Task.PRIORITY_NORMAL) {
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
				if (!rightsManager.hasRight(a, rightName, rightValues)) {
					request.getResponse().setStatus(403, "Request not allowed, it needs right " + rightName);
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
