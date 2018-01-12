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
		String value();
	}

	/** Specify that a service or method of a service needs a role to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Repeatable(RequireRoles.class)
	public @interface RequireRole {
		String value();
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	public @interface RequireRoles {
		RequireRole[] value();
	}

	/** Specify that a service or method of a service needs a right to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Repeatable(RequireBooleanRights.class)
	public @interface RequireBooleanRight {
		String name();
		boolean value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	public @interface RequireBooleanRights {
		RequireBooleanRight[] value();
	}
	
	/** Specify that a service or method of a service needs a right to be executed. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Repeatable(RequireIntegerRights.class)
	public @interface RequireIntegerRight {
		String name();
		int value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	public @interface RequireIntegerRights {
		RequireIntegerRight[] value();
	}
	
	/** The parameter should be created by deserializing the body. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Body {
		boolean required() default false;
	}

	/** The parameter should be read from the query string of the request. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Query {
		String name();
		boolean required() default false;
	}
	
}
