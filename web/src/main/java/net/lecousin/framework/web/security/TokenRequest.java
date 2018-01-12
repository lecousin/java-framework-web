package net.lecousin.framework.web.security;

/** Store a token for authentication, together with the type of token. */
public class TokenRequest implements IAuthenticationRequest {

	/** Constructor. */
	public TokenRequest() {
	}
	
	/** Constructor. */
	public TokenRequest(String type, String token) {
		this.type = type;
		this.token = token;
	}
	
	public String type;
	public String token;
	
}
