package net.lecousin.framework.web.services.soap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.lecousin.framework.web.services.WebService;

public interface SOAP extends WebService {

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Service {
		String defaultPath();
		String targetNamespace();
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface Operation {
		String action() default "";
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.PARAMETER, ElementType.FIELD})
	public @interface Header {
		String namespaceURI() default "";
		String localName();
		boolean required() default false;
	}

	/** Specify that the parameter is expecting a DOM element corresponding to the request,
	 * or for an operation result type it means any XML element may be returned. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.PARAMETER, ElementType.TYPE_USE})
	public @interface BodyElement {
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Message {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface MessageBody {
	}

}
