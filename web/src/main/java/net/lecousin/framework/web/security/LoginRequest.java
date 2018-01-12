package net.lecousin.framework.web.security;

/** Store a username and password for authentication. */
public class LoginRequest implements IAuthenticationRequest {

	/** Constructor. */
	public LoginRequest() {
	}
	
	/** Constructor. */
	public LoginRequest(String username, String password) {
		this.username = username;
		this.password = password;
	}
	
	public String username;
	public String password;
	
}
