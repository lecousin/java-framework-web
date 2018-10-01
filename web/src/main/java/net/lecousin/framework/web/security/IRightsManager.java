package net.lecousin.framework.web.security;

import java.util.Map;
import java.util.Set;

import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;

public interface IRightsManager {
	
	/** Return all roles of the user. */
	public Set<String> getRoles(IAuthentication auth);

	/** Return true if the user has the given role or is a super administrator. */
	public boolean hasRole(IAuthentication auth, String role);
	
	/** Return all rights of the user. */
	public Map<String, Boolean> getBooleanRights(IAuthentication auth);
	
	/** Return true if the given right and value are in the list of the user's rights or is a super administrator. */
	public boolean hasRight(IAuthentication auth, String name, boolean value);
	
	/** Return all rights of the user. */
	public Map<String, FragmentedRangeLong> getIntegerRights(IAuthentication auth);

	/** Return true if the given right and value are in the list of the user's rights or is a super administrator. */
	public boolean hasRight(IAuthentication auth, String name, FragmentedRangeLong acceptedValues);

	/** Return true if the given right and value are in the list of the user's rights or is a super administrator. */
	public default boolean hasRight(IAuthentication auth, String name, RangeLong acceptedValues) {
		return hasRight(auth, name, new FragmentedRangeLong(acceptedValues));
	}

	/** Return true if the given right and value are in the list of the user's rights or is a super administrator. */
	public default boolean hasRight(IAuthentication auth, String name, long acceptedValue) {
		return hasRight(auth, name, new FragmentedRangeLong(new RangeLong(acceptedValue, acceptedValue)));
	}

	/** Return an object that can be serialized by a web service, to describe the authenticated user. */
	public default Object getDescriptor(IAuthentication auth) {
		UserDescriptor user = new UserDescriptor();
		user.username = auth.getUsername();
		user.isAdmin = auth.isSuperAdmin();
		user.roles = getRoles(auth);
		user.booleanRights = getBooleanRights(auth);
		user.integerRights = getIntegerRights(auth);
		return user;
	}

}
