package net.lecousin.framework.web.security;

import java.util.List;
import java.util.Map;

import net.lecousin.framework.math.FragmentedRangeInteger;

/**
 * Interface to hold information about authenticated user or system.
 */
public interface IAuthentication {

	/** Return the username. */
	public String getUsername();
	
	/** Return true if this is a super administrator, who should access to everything. */
	public boolean isSuperAdmin();
	
	/** Return the list of roles the user has. */
	public List<String> getRoles();
	
	/** Return the list of boolean rights of the user. */
	public Map<String, Boolean> getBooleanRights();
	
	/** Return the list of integer rights of the user. */
	public Map<String, Integer> getIntegerRights();
	
	/** Return true if the user has the given role or is a super administrator. */
	public default boolean hasRole(String role) {
		return isSuperAdmin() || getRoles().contains(role);
	}
	
	/** Return true if the given right and value are in the list of the user's rights or is a super administrator. */
	public default boolean hasRight(String name, boolean value) {
		if (isSuperAdmin()) return true;
		Map<String, Boolean> rights = getBooleanRights();
		Boolean val = rights.get(name);
		return val != null && val.booleanValue() == value;
	}
	
	/** Return true if the given right and value are in the list of the user's rights or is a super administrator. */
	public default boolean hasRight(String name, int value) {
		if (isSuperAdmin()) return true;
		Map<String, Integer> rights = getIntegerRights();
		Integer val = rights.get(name);
		return val != null && val.intValue() == value;
	}
	
	/** Return true if the given right and value are in the list of the user's rights or is a super administrator. */
	public default boolean hasRight(String name, FragmentedRangeInteger acceptedValues) {
		if (isSuperAdmin()) return true;
		Map<String, Integer> rights = getIntegerRights();
		Integer val = rights.get(name);
		return val != null && acceptedValues.containsValue(val.intValue());
	}
	
	/** Return an object that can be serialized by a web service, to describe the authenticated user. */
	public default Object getDescriptor() {
		UserDescriptor user = new UserDescriptor();
		user.username = getUsername();
		user.isAdmin = isSuperAdmin();
		return user;
	}
	
}
