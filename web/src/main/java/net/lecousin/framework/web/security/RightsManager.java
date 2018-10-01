package net.lecousin.framework.web.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;

public class RightsManager implements IRightsManager {

	private static class Role {
		public Set<String> impliedRoles = new HashSet<>();
		public Map<String, FragmentedRangeLong> impliedIntegerRights = new HashMap<>();
		public Map<String, Boolean> impliedBooleanRights = new HashMap<>();
	}
	
	protected Map<String, Role> roles = new HashMap<>();
	
	public void declareRole(String name) {
		roles.put(name, new Role());
	}
	
	public void roleImply(String roleName, String impliedRoleName) {
		Role role = roles.get(roleName);
		if (role == null) return;
		role.impliedRoles.add(impliedRoleName);
	}
	
	public void roleImply(String roleName, String integerRightName, FragmentedRangeLong values) {
		Role role = roles.get(roleName);
		if (role == null) return;
		FragmentedRangeLong range = role.impliedIntegerRights.get(integerRightName);
		if (range == null)
			range = values;
		else
			range.addAll(values);
		role.impliedIntegerRights.put(integerRightName, range);
	}
	
	public void roleImply(String roleName, String integerRightName, RangeLong values) {
		roleImply(roleName, integerRightName, new FragmentedRangeLong(values));
	}
	
	public void roleImply(String roleName, String integerRightName, long value) {
		roleImply(roleName, integerRightName, new FragmentedRangeLong(new RangeLong(value, value)));
	}
	
	public void roleImply(String roleName, String booleanRightName, boolean value) {
		Role role = roles.get(roleName);
		if (role == null) return;
		Boolean v = role.impliedBooleanRights.get(booleanRightName);
		if (v == null || !v.booleanValue())
			role.impliedBooleanRights.put(booleanRightName, Boolean.valueOf(value));
	}
	
	@Override
	public Set<String> getRoles(IAuthentication auth) {
		if (auth.isSuperAdmin()) return roles.keySet();
		Set<String> list = new HashSet<>();
		for (String role : auth.getRoles())
			addRole(list, role);
		return list;
	}
	
	private void addRole(Set<String> list, String role) {
		if (list.contains(role)) return;
		list.add(role);
		Role r = roles.get(role);
		if (r == null) return;
		for (String name : r.impliedRoles)
			addRole(list, name);
	}
	
	@Override
	public boolean hasRole(IAuthentication auth, String role) {
		if (auth.isSuperAdmin()) return true;
		return hasRole(auth.getRoles(), role, new HashSet<>());
	}
	
	protected boolean hasRole(Collection<String> roles, String role, Set<String> checked) {
		for (String roleName : roles) {
			if (roleName.equals(role)) return true;
			if (checked.contains(roleName)) continue;
			Role r = this.roles.get(roleName);
			if (r == null) continue;
			checked.add(roleName);
			if (hasRole(r.impliedRoles, role, checked))
				return true;
		}
		return false;
	}
	
	@Override
	public Map<String, Boolean> getBooleanRights(IAuthentication auth) {
		Map<String, Boolean> list = new HashMap<>();
		list.putAll(auth.getBooleanRights());
		Set<String> done = new HashSet<>();
		for (String role : auth.getRoles())
			addBooleanRights(list, role, done);
		return list;
	}
	
	private void addBooleanRights(Map<String, Boolean> list, String role, Set<String> done) {
		if (done.contains(role)) return;
		done.add(role);
		Role r = roles.get(role);
		if (r == null) return;
		for (Map.Entry<String, Boolean> right : r.impliedBooleanRights.entrySet()) {
			Boolean b = list.get(right.getKey());
			if (b == null || !b.booleanValue())
				list.put(right.getKey(), right.getValue());
		}
		for (String name : r.impliedRoles)
			addBooleanRights(list, name, done);
	}
	
	@Override
	public boolean hasRight(IAuthentication auth, String name, FragmentedRangeLong acceptedValues) {
		if (auth.isSuperAdmin()) return true;
		FragmentedRangeLong values = auth.getIntegerRights().get(name);
		if (values != null && values.containsOneValueIn(acceptedValues))
			return true;
		return hasRight(auth.getRoles(), name, acceptedValues, new HashSet<>());
	}
	
	protected boolean hasRight(Collection<String> roles, String name, FragmentedRangeLong acceptedValues, Set<String> checked) {
		for (String roleName : roles) {
			if (checked.contains(roleName)) continue;
			Role r = this.roles.get(roleName);
			if (r == null) continue;
			checked.add(roleName);
			FragmentedRangeLong values = r.impliedIntegerRights.get(name);
			if (values != null && values.containsOneValueIn(acceptedValues))
				return true;
			if (hasRight(r.impliedRoles, name, acceptedValues, checked))
				return true;
		}
		return false;
	}
	
	@Override
	public Map<String, FragmentedRangeLong> getIntegerRights(IAuthentication auth) {
		Map<String, FragmentedRangeLong> list = new HashMap<>();
		list.putAll(auth.getIntegerRights());
		Set<String> done = new HashSet<>();
		for (String role : auth.getRoles())
			addIntegerRights(list, role, done);
		return list;
	}
	
	private void addIntegerRights(Map<String, FragmentedRangeLong> list, String role, Set<String> done) {
		if (done.contains(role)) return;
		done.add(role);
		Role r = roles.get(role);
		if (r == null) return;
		for (Map.Entry<String, FragmentedRangeLong> right : r.impliedIntegerRights.entrySet()) {
			FragmentedRangeLong ranges = list.get(right.getKey());
			if (ranges == null)
				list.put(right.getKey(), right.getValue());
			else {
				FragmentedRangeLong result = new FragmentedRangeLong();
				result.addAll(ranges);
				result.addCopy(right.getValue());
				list.put(right.getKey(), result);
			}
		}
		for (String name : r.impliedRoles)
			addIntegerRights(list, name, done);
	}
	
	@Override
	public boolean hasRight(IAuthentication auth, String name, boolean value) {
		if (auth.isSuperAdmin()) return true;
		Boolean b = auth.getBooleanRights().get(name);
		if (b != null)
			return b.booleanValue() == value;
		return hasRight(auth.getRoles(), name, value, new HashSet<>());
	}
	
	protected boolean hasRight(Collection<String> roles, String name, boolean value, Set<String> checked) {
		for (String roleName : roles) {
			if (checked.contains(roleName)) continue;
			Role r = this.roles.get(roleName);
			if (r == null) continue;
			checked.add(roleName);
			Boolean b = r.impliedBooleanRights.get(name);
			if (b != null)
				return b.booleanValue() == value;
			if (hasRight(r.impliedRoles, name, value, checked))
				return true;
		}
		return false;
	}
	
}
