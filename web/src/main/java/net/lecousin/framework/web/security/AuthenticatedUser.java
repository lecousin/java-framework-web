package net.lecousin.framework.web.security;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.math.FragmentedRangeLong;

public class AuthenticatedUser implements IAuthentication {

	@SuppressWarnings("unchecked")
	public AuthenticatedUser(String username, boolean superAdmin, List<String> roles, Map<String, Boolean> booleanRights, Map<String, FragmentedRangeLong> integerRights) {
		this.username = username;
		this.superAdmin = superAdmin;
		this.roles = roles != null ? Collections.unmodifiableList(roles) : Collections.EMPTY_LIST;
		this.booleanRights = booleanRights != null ? Collections.unmodifiableMap(booleanRights) : Collections.EMPTY_MAP;
		this.integerRights = integerRights != null ? Collections.unmodifiableMap(integerRights) : Collections.EMPTY_MAP;
	}
	
	protected String username;
	protected boolean superAdmin;
	protected List<String> roles;
	protected Map<String, Boolean> booleanRights;
	protected Map<String, FragmentedRangeLong> integerRights;

	@Override
	public String getUsername() { return username; }

	@Override
	public boolean isSuperAdmin() { return superAdmin; }

	@Override
	public List<String> getRoles() { return roles; }

	@Override
	public Map<String, Boolean> getBooleanRights() { return booleanRights; }

	@Override
	public Map<String, FragmentedRangeLong> getIntegerRights() { return integerRights; }
	
}
