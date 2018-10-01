package net.lecousin.framework.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.collections.ArrayUtil;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.web.WebRequest;

public class InternalUsersAuthenticationProvider implements IAuthenticationProvider {

	public InternalUsersAuthenticationProvider(List<InternalUser> users) {
		this.users = users;
	}
	
	protected List<InternalUser> users;
	
	public static class InternalUser extends AuthenticatedUser {
		public InternalUser(String username, byte[] passwordHash, String passwordHashAlgo, boolean superAdmin, List<String> roles, Map<String, Boolean> booleanRights, Map<String, FragmentedRangeLong> integerRights) throws NoSuchAlgorithmException {
			super(username, superAdmin, roles, booleanRights, integerRights);
			this.passwordHash = passwordHash;
			this.passwordHashAlgo = passwordHashAlgo;
			MessageDigest.getInstance(passwordHashAlgo);
		}

		public InternalUser(String username, String tokenType, String token, boolean superAdmin, List<String> roles, Map<String, Boolean> booleanRights, Map<String, FragmentedRangeLong> integerRights) {
			super(username, superAdmin, roles, booleanRights, integerRights);
			this.token = token;
		}
		
		protected byte[] passwordHash;
		protected String passwordHashAlgo;
		protected String tokenType;
		protected String token;
	}
	
	@Override
	public ISynchronizationPoint<Exception> authenticate(WebRequest request) {
		LoginRequest login = request.getAuthenticationRequest(LoginRequest.class);
		if (login != null) {
			Map<String, byte[]> hashed = new HashMap<>();
			for (InternalUser user : users) {
				if (user.passwordHash == null) continue;
				if (!user.getUsername().equals(login.username)) continue;
				byte[] hash = hashed.get(user.passwordHashAlgo);
				if (hash == null) {
					try {
						hash = MessageDigest.getInstance(user.passwordHashAlgo).digest(login.password.getBytes(StandardCharsets.UTF_8));
					} catch (NoSuchAlgorithmException e) {
						// cannot happen, it was already checked in the constructor
					}
					hashed.put(user.passwordHashAlgo, hash);
				}
				if (!ArrayUtil.equals(hash, user.passwordHash)) continue;
				request.addAuthentication(this, user);
				return new SynchronizationPoint<>(true);
			}
		}
		TokenRequest token = request.getAuthenticationRequest(TokenRequest.class);
		if (token != null) {
			for (InternalUser user : users) {
				if (user.token == null) continue;
				if (!user.token.equals(token.token) || !user.tokenType.equals(token.type)) continue;
				request.addAuthentication(this, user);
				return new SynchronizationPoint<>(true);
			}
		}
		return new SynchronizationPoint<>(true);
	}
	
	@Override
	public void deconnect(IAuthentication auth, WebRequest request) {
	}

}
