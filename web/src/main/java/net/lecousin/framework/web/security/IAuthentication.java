package net.lecousin.framework.web.security;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.math.FragmentedRangeLong;

/**
 * Interface to hold information about authenticated user or system.
 */
public interface IAuthentication extends Serializable {
	
	/** Return the username. */
	public String getUsername();
	
	/** Return true if this is a super administrator, who should access to everything. */
	public boolean isSuperAdmin();
	
	/** Return the list of roles the user has. */
	public List<String> getRoles();
	
	/** Return the list of boolean rights of the user. */
	public Map<String, Boolean> getBooleanRights();
	
	/** Return the list of integer rights of the user. */
	public Map<String, FragmentedRangeLong> getIntegerRights();
	
}
