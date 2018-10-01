package net.lecousin.framework.web.security;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.web.WebRequest;

/**
 * Interface for an authentication provider.
 * An authentication provider is capable to authenticate a user.
 * It can retrieve a session from the request to retrieve the authenticated user.
 * If not yet authenticated, it can use one of the authentication request present in the request,
 * or do its own way.
 * Once the authentication succeed, it must store the resulting IAuthentication in the request.
 * If no authentication is done, this must not be an error. An error is returned only if
 * given authentication parameters are invalid and the user is not yet authenticated.
 */
public interface IAuthenticationProvider {

	/** Authenticate the user, and store the IAuthentication into the request. */
	public ISynchronizationPoint<Exception> authenticate(WebRequest request);
	
	/** Deconnect the given user and clear any data in the request. auth may be null. */
	public void deconnect(IAuthentication auth, WebRequest request);
	
}
