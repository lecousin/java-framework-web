package net.lecousin.framework.web.servlet3.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.LoginRequest;

public class TestAuthenticationProvider implements IAuthenticationProvider {

	@Override
	public ISynchronizationPoint<Exception> authenticate(WebRequest request) {
		LoginRequest login = request.getAuthenticationRequest(LoginRequest.class);
		if (login == null)
			return new SynchronizationPoint<>(true);
		if ("guillaume".equals(login.username)) {
			request.addAuthentication(this, new TestAuth("guillaume", "Role1", "Role2"));
			return new SynchronizationPoint<>(true);
		}
		if ("robert".equals(login.username)) {
			request.addAuthentication(this, new TestAuth("robert", "Role1"));
			return new SynchronizationPoint<>(true);
		}
		if ("dupont".equals(login.username)) {
			request.addAuthentication(this, new TestAuth("dupont", "Role2"));
			return new SynchronizationPoint<>(true);
		}
		if ("durand".equals(login.username)) {
			request.addAuthentication(this, new TestAuth("durand"));
			return new SynchronizationPoint<>(true);
		}
		return new SynchronizationPoint<>(new Exception("you are unknown here"));
	}
	
	@Override
	public void deconnect(IAuthentication auth, WebRequest request) {
	}
	
	private static class TestAuth implements IAuthentication {
		public TestAuth(String username, String... roles) {
			this.username = username;
			this.roles = Arrays.asList(roles);
		}
		
		private String username;
		private List<String> roles;

		@Override
		public String getUsername() {
			return username;
		}
		
		@Override
		public boolean isSuperAdmin() {
			return false;
		}

		@Override
		public List<String> getRoles() {
			return roles;
		}

		@Override
		public Map<String, Boolean> getBooleanRights() {
			return new HashMap<>();
		}
		
		@Override
		public Map<String, FragmentedRangeLong> getIntegerRights() {
			return new HashMap<>();
		}
	}
	
}
