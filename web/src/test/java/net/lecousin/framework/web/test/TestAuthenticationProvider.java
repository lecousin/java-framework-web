package net.lecousin.framework.web.test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.LoginRequest;
import net.lecousin.framework.web.security.TokenRequest;

public class TestAuthenticationProvider implements IAuthenticationProvider {

	@Override
	public ISynchronizationPoint<Exception> authenticate(WebRequest request) {
		String username = null;
		LoginRequest login = request.getAuthenticationRequest(LoginRequest.class);
		if (login != null)
			username = login.username;
		if (username == null) {
			TokenRequest tok = request.getAuthenticationRequest(TokenRequest.class);
			if (tok != null && "test".equals(tok.type) && tok.token != null && tok.token.startsWith("magic-token-")) {
				username = tok.token.substring(12);
			}
		}
		if (username == null)
			return new SynchronizationPoint<>(true);
		TestAuth user = users.get(username);
		if (user != null) {
			request.addAuthentication(this, user);
			return new SynchronizationPoint<>(true);
		}
		return new SynchronizationPoint<>(new Exception("you are unknown here"));
	}
	
	@Override
	public void deconnect(IAuthentication auth, WebRequest request) {
	}
	
	public TestAuthenticationProvider() {
		TestAuth user;
		user = new TestAuth("guillaume");
		user.getRoles().add("Role1");
		user.getRoles().add("Role2");
		user.getBooleanRights().put("b1", Boolean.TRUE);
		user.getBooleanRights().put("b2", Boolean.TRUE);
		user.getIntegerRights().put("i1", new FragmentedRangeLong(new RangeLong(51, 51)));
		user.getIntegerRights().put("i2", new FragmentedRangeLong(new RangeLong(3, 3)));
		users.put("guillaume", user);
		
		user = new TestAuth("robert");
		user.getRoles().add("Role1");
		user.getBooleanRights().put("b1", Boolean.TRUE);
		user.getBooleanRights().put("b2", Boolean.FALSE);
		user.getIntegerRights().put("i1", new FragmentedRangeLong(new RangeLong(10, 10)));
		user.getIntegerRights().put("i2", new FragmentedRangeLong(new RangeLong(0, 0)));
		users.put("robert", user);
		
		user = new TestAuth("dupont");
		user.getRoles().add("Role2");
		user.getBooleanRights().put("b2", Boolean.TRUE);
		user.getIntegerRights().put("i2", new FragmentedRangeLong(new RangeLong(5, 5)));
		users.put("dupont", user);
		
		user = new TestAuth("durand");
		users.put("durand", user);
		
		user = new TestAuth("root");
		users.put("root", user);
	}
	
	private Map<String, TestAuth> users = new HashMap<>();
	
	private static class TestAuth implements IAuthentication {
		public TestAuth(String username) {
			this.username = username;
		}
		
		private String username;
		private List<String> roles = new LinkedList<>();
		private Map<String, Boolean> booleanRights = new HashMap<>();
		private Map<String, FragmentedRangeLong> integerRights = new HashMap<>();

		@Override
		public String getUsername() {
			return username;
		}
		
		@Override
		public boolean isSuperAdmin() {
			return "root".equals(username);
		}

		@Override
		public List<String> getRoles() {
			return roles;
		}

		@Override
		public Map<String, Boolean> getBooleanRights() {
			return booleanRights;
		}
		
		@Override
		public Map<String, FragmentedRangeLong> getIntegerRights() {
			return integerRights;
		}
	}
	
}
