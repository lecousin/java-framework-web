package net.lecousin.framework.web.services.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.web.services.WebService;

public interface REST extends WebService {

	/** A resource may be an individual resource, or a collection of elements */
	public enum ResourceType {
		INDIVIDUAL,
		MULTIPLE
	}
	
	/** Declares a resource */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Resource {
		/** type of resource */
		ResourceType type();
		/** path: for a root resource, this may be overridden by the configuration */
		String path();
	}

	/**
	 * Retrieve a resource. In a multiple resource, it must contain an Id.
	 * This is the method called when a GET request is received.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface GetResource {
	}

	/**
	 * Method on a resource. If the name is empty, the name of the method is used.<br/>
	 * A method is accessed using a request, and the method name as sub-path.<br/>
	 * In case of a multiple resource, if the method has an Id parameter,
	 * a resource identifier must be present before the method name in the path.<br/>
	 * By default, a POST is used but another method may be specified.
	 * In case of a GET, a Body is not allowed.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface Method {
		String name() default "";
		HTTPRequest.Method method() default HTTPRequest.Method.POST;
	}
	
	/**
	 * For a multiple resource, declares the method to list elements when a GET request is received without sub-path
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface ListResources {
	}
	
	/**
	 * For a multiple resource, declares the method to create a new element when a POST request is received without sub-path
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface CreateResource {
	}

	/**
	 * Method used when a POST request is received.
	 * In case of a multiple resource, if the method has an Id parameter,
	 * the path must be a resource identifier, else the path must be empty. 
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface Post {
	}

	/**
	 * Method used when a PUT request is received.
	 * In case of a multiple resource, if the method has an Id parameter,
	 * the path must be a resource identifier, else the path must be empty. 
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface UpdateResource {
	}

	/**
	 * Method used when a DELETE request is received.
	 * In case of a multiple resource, if the method has an Id parameter,
	 * the path must be a resource identifier, else the path must be empty. 
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface DeleteResource {
	}
	

	/**
	 * Parameter that will receive the resource id, for a multiple resource.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Id {
	}

}
