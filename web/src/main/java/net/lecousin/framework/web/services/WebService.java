package net.lecousin.framework.web.services;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Common annotations for web services. */
public interface WebService {
	
	/** Description of the web service, for documentation. It may contain HTML tags. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Description {
		/** Description. */
		String value();
	}

	/** Specify that a service or method of a service needs a role to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Repeatable(RequireRoles.class)
	public @interface RequireRole {
		/** Required role. */
		String value();
	}
	
	/** Specify that a service or method of a service needs roles to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	public @interface RequireRoles {
		/** Required roles. */
		RequireRole[] value();
	}

	/** Specify that a service or method of a service needs a right to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Repeatable(RequireBooleanRights.class)
	public @interface RequireBooleanRight {
		/** Right name. */
		String name();
		/** Right value. */
		boolean value();
	}

	/** Specify that a service or method of a service needs rights to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	public @interface RequireBooleanRights {
		/** Required rights. */
		RequireBooleanRight[] value();
	}
	
	/** Specify that a service or method of a service needs a right to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Repeatable(RequireIntegerRights.class)
	public @interface RequireIntegerRight {
		/** Right name. */
		String name();
		/** Right value. */
		long value();
	}

	/** Specify that a service or method of a service needs rights to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	public @interface RequireIntegerRights {
		/** Required rights. */
		RequireIntegerRight[] value();
	}
	
	/** The parameter should be created by deserializing the body. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Body {
		/** if false and there is no body, null is given. */
		boolean required() default false;
	}

	/** The parameter should be read from the query string of the request. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Query {
		/** Query parameter name. */
		String name();
		/** if false and there is no body, null is given. */
		boolean required() default false;
	}
	
}
