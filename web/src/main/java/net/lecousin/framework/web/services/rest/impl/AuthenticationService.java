package net.lecousin.framework.web.services.rest.impl;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.IRightsManager;
import net.lecousin.framework.web.security.LoginRequest;
import net.lecousin.framework.web.security.TokenRequest;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.rest.REST;

@REST.Resource(type=REST.ResourceType.INDIVIDUAL, path="auth")
@WebService.Description("Provides authentication functionalities.")
public class AuthenticationService implements REST {

	@Inject
	IAuthenticationProvider provider;
	@Inject
	IRightsManager rightsManager;
	
	@REST.GetResource
	public Object get(WebRequest request) {
		IAuthentication auth = request.getAuthentication(provider);
		if (auth == null) return null;
		return rightsManager.getDescriptor(auth);
	}
	
	@REST.Method
	public AsyncWork<Object, Exception> authenticate(WebRequest req) {
		AsyncWork<IAuthentication, Exception> auth = req.authenticate(provider);
		AsyncWork<Object, Exception> result = new AsyncWork<>();
		auth.listenInline((a) -> { result.unblockSuccess(a == null ? null : rightsManager.getDescriptor(a)); }, result);
		return result;
	}
	
	@REST.Method
	public AsyncWork<Object, Exception> login(@WebService.Body LoginRequest login, WebRequest req) {
		req.addAuthenticationRequest(login);
		AsyncWork<IAuthentication, Exception> auth = req.authenticate(provider);
		AsyncWork<Object, Exception> result = new AsyncWork<>();
		auth.listenInline((a) -> { result.unblockSuccess(a == null ? null : rightsManager.getDescriptor(a)); }, result);
		return result;
	}
	
	@REST.Method
	public AsyncWork<Object, Exception> token(@WebService.Body TokenRequest token, WebRequest req) {
		req.addAuthenticationRequest(token);
		AsyncWork<IAuthentication, Exception> auth = req.authenticate(provider);
		AsyncWork<Object, Exception> result = new AsyncWork<>();
		auth.listenInline((a) -> { result.unblockSuccess(a == null ? null : rightsManager.getDescriptor(a)); }, result);
		return result;
	}
	
	@REST.Method
	public void deconnect(WebRequest req) {
		req.clearAuthentication(provider);
	}
	
}
