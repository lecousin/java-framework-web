package net.lecousin.framework.web.services.rest;

import java.util.LinkedList;
import java.util.List;

import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.network.http.HTTPRequest;

public class RESTOperationDescription {

	public String description;
	public HTTPRequest.Method httpMethod;
	public String path;
	
	public java.lang.reflect.Method restMethod;
	
	public boolean needsAuthentication;
	
	public List<Parameter> parameters = new LinkedList<>();
	public String idParameter;
	public TypeDefinition bodyType;
	public boolean bodyRequired = false;
	
	public TypeDefinition returnType;
	
	public static class Parameter {
		public Parameter(String name, Class<?> type, boolean required) {
			this.name = name;
			this.type = type;
			this.required = required;
		}
		public String name;
		public Class<?> type;
		public boolean required;
	}
	
}
