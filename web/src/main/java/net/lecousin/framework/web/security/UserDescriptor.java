package net.lecousin.framework.web.security;

import java.util.Map;
import java.util.Set;

import net.lecousin.framework.math.FragmentedRangeLong;

/** Simple class that can be serialized to send information about the authenticated user. */
public class UserDescriptor {

	public String username;
	
	public boolean isAdmin;
	
	public Set<String> roles;
	
	public Map<String, Boolean> booleanRights;
	
	public Map<String, FragmentedRangeLong> integerRights;
	
}
