package net.lecousin.framework.web.services.rest;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.injection.Injection;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.io.buffering.MemoryIO;
import net.lecousin.framework.io.out2in.OutputToInput;
import net.lecousin.framework.io.out2in.OutputToInputBuffers;
import net.lecousin.framework.io.serialization.Serializer;
import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.json.JSONSerializer;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.network.http.server.HTTPServerProtocol;
import net.lecousin.framework.network.http.server.HTTPServerResponse;
import net.lecousin.framework.network.mime.MimeHeader;
import net.lecousin.framework.network.mime.MimeMessage;
import net.lecousin.framework.network.mime.header.ParameterizedHeaderValue;
import net.lecousin.framework.network.mime.header.ParameterizedHeaderValues;
import net.lecousin.framework.network.server.TCPServerClient;
import net.lecousin.framework.network.session.Session;
import net.lecousin.framework.plugins.ExtensionPoints;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Triple;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebResourcesBundle;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.IRightsManager;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.WebService.Body;
import net.lecousin.framework.web.services.WebService.Query;
import net.lecousin.framework.web.services.WebServiceProvider;
import net.lecousin.framework.web.services.WebServiceUtils;
import net.lecousin.framework.web.services.rest.REST.Id;

public class RESTWebServiceProvider extends WebServiceProvider<REST> {
	
	public RESTWebServiceProvider(WebResourcesBundle bundle, REST rest) throws Exception {
		super(bundle, rest);
		this.logger = LCCore.getApplication().getLoggerFactory().getLogger(RESTWebServiceProvider.class);
		resource = rest.getClass().getAnnotation(REST.Resource.class);
		if (resource == null)
			throw new Exception("Class "+rest.getClass().getName()+" must have the REST.Resource annotation");
		globalRoles = rest.getClass().getAnnotationsByType(WebService.RequireRole.class);
		globalBooleanRights = rest.getClass().getAnnotationsByType(WebService.RequireBooleanRight.class);
		globalIntegerRights = rest.getClass().getAnnotationsByType(WebService.RequireIntegerRight.class);
		parseService(bundle);
	}
	
	@Inject(required = false)
	private IAuthenticationProvider authenticationProvider;
	@Inject(required = false)
	private IRightsManager rightsManager;

	private Logger logger;
	private REST.Resource resource;
	private WebService.RequireRole[] globalRoles;
	private WebService.RequireBooleanRight[] globalBooleanRights;
	private WebService.RequireIntegerRight[] globalIntegerRights;
	
	private List<RestMethod> getMethods = new ArrayList<>();
	private List<RestMethod> postMethods = new ArrayList<>();
	private List<RestMethod> putMethods = new ArrayList<>();
	private List<RestMethod> deleteMethods = new ArrayList<>();
	private List<RestMethod> subResources = new ArrayList<>();
	
	@Override
	public String getServiceTypeName() {
		return "REST";
	}
	
	@Override
	public String getDefaultPath() {
		return resource.path();
	}
	
	private static class RestMethod {
		/** empty string if no sub-path, null if it is any string (an id), or the first sub-path element to match with */
		private String firstPathElement;
		/** empty string if no sub-path, null for any sub-path (for sub-resources) */
		private String secondPathElement;
		/** method or sub resource */
		private Method method;
		private RESTWebServiceProvider subResource;
		
		private WebService.RequireRole[] specificRoles;
		private WebService.RequireBooleanRight[] specificBooleanRights;
		private WebService.RequireIntegerRight[] specificIntegerRights;
	}

	private void parseService(WebResourcesBundle bundle) throws Exception {
		for (Method m : service.getClass().getMethods()) {
			String used = null;
			
			WebService.RequireRole[] specificRoles = m.getAnnotationsByType(WebService.RequireRole.class);
			WebService.RequireBooleanRight[] specificBooleanRights = m.getAnnotationsByType(WebService.RequireBooleanRight.class);
			WebService.RequireIntegerRight[] specificIntegerRights = m.getAnnotationsByType(WebService.RequireIntegerRight.class);
			
			// @GetResource
			REST.GetResource get = m.getAnnotation(REST.GetResource.class);
			if (get != null) {
				/*
				if (used != null)
					throw new Exception("A method cannot have both annotations GetResource and "+used);
				*/
				RestMethod rm = new RestMethod();
				rm.method = m;
				rm.specificRoles = specificRoles;
				rm.specificBooleanRights = specificBooleanRights;
				rm.specificIntegerRights = specificIntegerRights;
				if (REST.ResourceType.MULTIPLE.equals(resource.type()))
					rm.firstPathElement = null; // id expected
				else
					rm.firstPathElement = ""; // no sub-path
				rm.secondPathElement = ""; // nothing expected
				checkParameters(m, REST.ResourceType.MULTIPLE.equals(resource.type()), REST.ResourceType.MULTIPLE.equals(resource.type()), false);
				getMethods.add(rm);
				used = "GetResource";
			}
			
			// @Method
			REST.Method method = m.getAnnotation(REST.Method.class);
			if (method != null) {
				if (used != null)
					throw new Exception("A method cannot have both annotations Method and "+used);
				RestMethod rm = new RestMethod();
				rm.method = m;
				rm.specificRoles = specificRoles;
				rm.specificBooleanRights = specificBooleanRights;
				rm.specificIntegerRights = specificIntegerRights;
				String name = method.name();
				if (name.length() == 0) name = m.getName();
				boolean hasId = checkParameters(m, false, REST.ResourceType.MULTIPLE.equals(resource.type()), true);
				if (hasId) {
					rm.firstPathElement = null; // id expected
					rm.secondPathElement = name;
				} else {
					rm.firstPathElement = name;
					rm.secondPathElement = ""; // nothing expected
				}
				switch (method.method()) {
				case GET: getMethods.add(rm); break;
				case POST: postMethods.add(rm); break;
				case PUT: putMethods.add(rm); break;
				case DELETE: deleteMethods.add(rm); break;
				default: throw new Exception("Method " + method.method().name() + " is not supported for @REST.Method annotation");
				}
				used = "Method";
			}
			
			// @ListResources
			REST.ListResources list = m.getAnnotation(REST.ListResources.class);
			if (list != null) {
				if (used != null)
					throw new Exception("A method cannot have both annotations ListResources and "+used);
				if (!REST.ResourceType.MULTIPLE.equals(resource.type()))
					throw new Exception("Method "+m.getName()+" is declared with @ListResources but class "+service.getClass().getName()+" is declared as an individual resource");
				RestMethod rm = new RestMethod();
				rm.method = m;
				rm.specificRoles = specificRoles;
				rm.specificBooleanRights = specificBooleanRights;
				rm.specificIntegerRights = specificIntegerRights;
				checkParameters(m, false, false, false);
				rm.firstPathElement = "";
				rm.secondPathElement = "";
				getMethods.add(rm);
				used = "ListResources";
			}
			
			// @CreateResource
			REST.CreateResource create = m.getAnnotation(REST.CreateResource.class);
			if (create != null) {
				if (used != null)
					throw new Exception("A method cannot have both annotations CreateResource and "+used);
				if (!REST.ResourceType.MULTIPLE.equals(resource.type()))
					throw new Exception("Method "+m.getName()+" is declared with @CreateResource but class "+service.getClass().getName()+" is declared as an individual resource");
				RestMethod rm = new RestMethod();
				rm.method = m;
				rm.specificRoles = specificRoles;
				rm.specificBooleanRights = specificBooleanRights;
				rm.specificIntegerRights = specificIntegerRights;
				checkParameters(m, false, false, true);
				rm.firstPathElement = "";
				rm.secondPathElement = "";
				postMethods.add(rm);
				used = "CreateResource";
			}
			
			// @Post
			REST.Post post = m.getAnnotation(REST.Post.class);
			if (post != null) {
				if (used != null)
					throw new Exception("A method cannot have both annotations Post and "+used);
				RestMethod rm = new RestMethod();
				rm.method = m;
				rm.specificRoles = specificRoles;
				rm.specificBooleanRights = specificBooleanRights;
				rm.specificIntegerRights = specificIntegerRights;
				boolean hasId = checkParameters(m, false, REST.ResourceType.MULTIPLE.equals(resource.type()), true);
				if (hasId) {
					rm.firstPathElement = null; // id expected
					rm.secondPathElement = "";
				} else {
					rm.firstPathElement = "";
					rm.secondPathElement = ""; // nothing expected
				}
				postMethods.add(rm);
				used = "Post";
			}
			
			// @UpdateResource
			REST.UpdateResource update = m.getAnnotation(REST.UpdateResource.class);
			if (update != null) {
				if (used != null)
					throw new Exception("A method cannot have both annotations UpdateResource and "+used);
				RestMethod rm = new RestMethod();
				rm.method = m;
				rm.specificRoles = specificRoles;
				rm.specificBooleanRights = specificBooleanRights;
				rm.specificIntegerRights = specificIntegerRights;
				boolean hasId = checkParameters(m, false, REST.ResourceType.MULTIPLE.equals(resource.type()), true);
				if (hasId) {
					rm.firstPathElement = null; // id expected
					rm.secondPathElement = "";
				} else {
					rm.firstPathElement = "";
					rm.secondPathElement = ""; // nothing expected
				}
				putMethods.add(rm);
				used = "UpdateResource";
			}
			
			// @DeleteResource
			REST.DeleteResource delete = m.getAnnotation(REST.DeleteResource.class);
			if (delete != null) {
				if (used != null)
					throw new Exception("A method cannot have both annotations DeleteResource and "+used);
				RestMethod rm = new RestMethod();
				rm.method = m;
				rm.specificRoles = specificRoles;
				rm.specificBooleanRights = specificBooleanRights;
				rm.specificIntegerRights = specificIntegerRights;
				boolean hasId = checkParameters(m, false, REST.ResourceType.MULTIPLE.equals(resource.type()), false);
				if (hasId) {
					rm.firstPathElement = null; // id expected
					rm.secondPathElement = "";
				} else {
					rm.firstPathElement = "";
					rm.secondPathElement = ""; // nothing expected
				}
				deleteMethods.add(rm);
				used = "DeleteResource";
			}
			
		}
		
		// @Resource for sub-resources
		for (Class<?> innerClass : service.getClass().getClasses()) {
			REST.Resource res = innerClass.getAnnotation(REST.Resource.class);
			if (res == null) continue;
			if (innerClass.isInterface())
				throw new Exception("@Resource annotation found on interface "+innerClass.getName()+". A resource must be instantiable.");
			if ((innerClass.getModifiers() & (Modifier.PUBLIC | Modifier.ABSTRACT)) != Modifier.PUBLIC)
				throw new Exception("@Resource annotation found on inner class "+innerClass.getName()+". A resource must be public and not abstract.");
			if (!REST.class.isAssignableFrom(innerClass))
				throw new Exception("Resource inner class "+innerClass.getName()+" must implement the REST interface");
			Object instance;
			if ((innerClass.getModifiers() & Modifier.STATIC) == 0) {
				Constructor<?> ctor = innerClass.getDeclaredConstructor(service.getClass());
				instance = ctor.newInstance(service);
			} else
				instance = innerClass.newInstance();
			Injection.inject(bundle.getInjectionContext(), instance);
			RestMethod rm = new RestMethod();
			if (REST.ResourceType.MULTIPLE.equals(resource.type())) {
				rm.firstPathElement = null; // id expected
				rm.secondPathElement = res.path();
			} else {
				rm.firstPathElement = res.path();
				rm.secondPathElement = null; // any
			}
			rm.subResource = new RESTWebServiceProvider(bundle, (REST)instance);
			subResources.add(rm);
		}
		
		if (logger.debug()) {
			StringBuilder s = new StringBuilder();
			s.append("REST Service loaded: ").append(service.getClass().getName()).append("\r\n");
			for (RestMethod rm : getMethods)
				s.append(" - GET: ").append(rm.firstPathElement).append('/').append(rm.secondPathElement).append('=').append(rm.method.getName()).append("\r\n");
			for (RestMethod rm : postMethods)
				s.append(" - POST: ").append(rm.firstPathElement).append('/').append(rm.secondPathElement).append('=').append(rm.method.getName()).append("\r\n");
			for (RestMethod rm : putMethods)
				s.append(" - PUT: ").append(rm.firstPathElement).append('/').append(rm.secondPathElement).append('=').append(rm.method.getName()).append("\r\n");
			for (RestMethod rm : deleteMethods)
				s.append(" - DELETE: ").append(rm.firstPathElement).append('/').append(rm.secondPathElement).append('=').append(rm.method.getName()).append("\r\n");
			for (RestMethod rm : subResources)
				s.append(" - SUB-RESOURCE: ").append(rm.firstPathElement).append('/').append(rm.secondPathElement).append("\r\n");
			logger.debug(s.toString());
		}
	}
	
	private static boolean checkParameters(Method method, boolean idExpected, boolean idAllowed, boolean bodyAllowed) throws Exception {
		boolean idFound = false;
		for (Parameter p : method.getParameters()) {
			Id id = p.getAnnotation(Id.class);
			Body body = p.getAnnotation(Body.class);
			Query query = p.getAnnotation(Query.class);
			if (id != null) {
				if (!idAllowed)
					throw new Exception("Unexpected Id annotation in parameter "+p.getName()+" of method "+method.getName()+" in REST resource class "+method.getDeclaringClass().getName());
				if (body != null)
					throw new Exception("Id and Body cannot be used on the same parameter "+p.getName()+" in method "+method.getName()+" of REST resource class "+method.getDeclaringClass().getName());
				if (query != null)
					throw new Exception("Id and Param cannot be used on the same parameter "+p.getName()+" in method "+method.getName()+" of REST resource class "+method.getDeclaringClass().getName());
				// TODO check integer or string
				idFound = true;
				continue;
			}
			if (body != null) {
				if (!bodyAllowed)
					throw new Exception("Unexpected Body annotation in parameter "+p.getName()+" of method "+method.getName()+" in REST resource class "+method.getDeclaringClass().getName());
				if (query != null)
					throw new Exception("Body and Param cannot be used on the same parameter "+p.getName()+" in method "+method.getName()+" of REST resource class "+method.getDeclaringClass().getName());
				continue;
			}
			if (query != null) {
				// TODO check integer or string
				continue;
			}
			// TODO check the type can be injected
		}
		if (idExpected && !idFound)
			throw new Exception("Missing parameter with Id annotation in method "+method.getName()+" of REST resource class "+method.getDeclaringClass().getName());
		return idFound;
	}
	
	@Override
	public List<OperationDescription> getOperations() {
		List<RESTOperationDescription> ops = getOperationsDescriptions();
		ArrayList<OperationDescription> list = new ArrayList<>(ops.size());
		for (RESTOperationDescription op : ops)
			list.add(new OperationDescription(op.httpMethod.name() + " " + op.path, op.description != null ? op.description : "No description"));
		return list;
	}
	
	public List<RESTOperationDescription> getOperationsDescriptions() {
		LinkedList<RESTOperationDescription> list = new LinkedList<>();
		for (RestMethod rm : getMethods)
			list.add(getDescription(HTTPRequest.Method.GET, rm));
		for (RestMethod rm : postMethods)
			list.add(getDescription(HTTPRequest.Method.POST, rm));
		for (RestMethod rm : putMethods)
			list.add(getDescription(HTTPRequest.Method.PUT, rm));
		for (RestMethod rm : deleteMethods)
			list.add(getDescription(HTTPRequest.Method.DELETE, rm));
		for (RestMethod sr : subResources) {
			List<RESTOperationDescription> ops = sr.subResource.getOperationsDescriptions();
			for (RESTOperationDescription op : ops) {
				op.path = sr.firstPathElement + '/' + sr.secondPathElement + '/' + op.path;
				list.add(op);
			}
		}
		return list;
		
	}
	
	private RESTOperationDescription getDescription(HTTPRequest.Method method, RestMethod rm) {
		RESTOperationDescription op = new RESTOperationDescription();
		op.httpMethod = method;
		op.restMethod = rm.method;
		WebService.Description d = rm.method.getAnnotation(WebService.Description.class);
		op.description = d != null ? d.value() : null;
		op.needsAuthentication = needsAuthentication(rm);
		Parameter[] paramsDef = rm.method.getParameters();
		for (Parameter p : paramsDef) {
			if (p.getAnnotation(REST.Id.class) != null) {
				op.idParameter = p.getName();
				op.parameters.add(new RESTOperationDescription.Parameter(op.idParameter, p.getType(), true));
			} else {
				WebService.Query q = p.getAnnotation(WebService.Query.class);
				if (q != null)
					op.parameters.add(new RESTOperationDescription.Parameter(q.name(), p.getType(), q.required()));
				else {
					WebService.Body b = p.getAnnotation(WebService.Body.class);
					if (b != null) {
						op.bodyType = new TypeDefinition(new TypeDefinition(rm.method.getDeclaringClass()), p.getParameterizedType());
						op.bodyRequired = b.required();
					}
				}
			}
		}
		
		Class<?> type = rm.method.getReturnType();
		if (AsyncWork.class.isAssignableFrom(type)) {
			Type t = rm.method.getGenericReturnType();
			if (t instanceof ParameterizedType) {
				Type[] types = ((ParameterizedType)t).getActualTypeArguments();
				if (types.length == 2 && !Void.class.equals(types[0]))
					op.returnType = new TypeDefinition(new TypeDefinition(rm.method.getDeclaringClass()), types[0]);
			}
		} else if (!void.class.equals(type))
			op.returnType = new TypeDefinition(new TypeDefinition(rm.method.getDeclaringClass()), rm.method.getGenericReturnType());

		StringBuilder path = new StringBuilder(64);
		if (rm.firstPathElement == null)
			path.append("/{").append(op.idParameter != null ? op.idParameter : "id").append("}");
		else
			path.append('/').append(rm.firstPathElement);
		if (rm.secondPathElement != null && rm.secondPathElement.length() < 0)
			path.append('/').append(rm.secondPathElement);
		op.path = path.toString();
		return op;
	}
	
	@Override
	public List<WebServiceSpecification> getSpecifications() {
		ArrayList<WebServiceSpecification> list = new ArrayList<>();
		for (RESTSpecificationPlugin spec : ExtensionPoints.getExtensionPoint(RESTSpecificationExtension.class).getPlugins()) {
			list.add(new WebServiceSpecification(spec.getName(), "specification/" + spec.getId()));
		}
		return list;
	}

	@Override
	public Object checkProcessing(WebRequest request) {
		String subpath = request.getSubPath();
		String first, second, remaining;
		if (subpath.length() == 0) {
			first = "";
			second = "";
			remaining = "";
		} else {
			int i = subpath.indexOf('/');
			if (i < 0) {
				first = subpath;
				second = "";
				remaining = "";
			} else {
				first = subpath.substring(0,i);
				second = subpath.substring(i+1);
				i = second.indexOf('/');
				if (i < 0)
					remaining = "";
				else {
					remaining = second.substring(i+1);
					second = second.substring(0, i+1);
				}
			}
		}
		if ("specification".equals(first)) {
			for (RESTSpecificationPlugin spec : ExtensionPoints.getExtensionPoint(RESTSpecificationExtension.class).getPlugins()) {
				if (spec.getId().equals(second)) {
					return spec;
				}
			}
		}
		List<RestMethod> methods;
		switch (request.getRequest().getMethod()) {
		case GET: methods = getMethods; break;
		case POST: methods = postMethods; break;
		case PUT: methods = putMethods; break;
		case DELETE: methods = deleteMethods; break;
		default: return null;
		}
		for (RestMethod rm : methods) {
			String resourceId = null;
			if (rm.firstPathElement == null) {
				// id expected
				if (first.length() == 0) continue;
				resourceId = first;
			} else if (rm.firstPathElement.length() == 0) {
				if (first.length() > 0) continue;
			} else {
				if (!rm.firstPathElement.equals(first)) continue;
			}
			if (rm.secondPathElement == null) {
				// any
			} else if (rm.secondPathElement.length() == 0) {
				if (second.length() > 0) continue;
			} else {
				if (!rm.secondPathElement.equals(second)) continue;
			}
			// found a matching method
			return new Pair<>(rm, resourceId);
		}
		// sub-resources
		if (first.length() == 0) return null;
		for (RestMethod rm : subResources) {
			String myPath = request.getCurrentPath();
			String mp, sp;
			if (rm.firstPathElement == null) {
				// first is id, second is the sub-resource name
				if (!second.equals(rm.secondPathElement)) continue;
				mp = myPath+first+'/'+second+'/';
				sp = remaining;
			} else {
				// first is the sub-resource name
				if (!first.equals(rm.firstPathElement)) continue;
				sp = second;
				if (sp.length() > 0) sp += '/';
				sp += remaining;
				mp = myPath+first+'/';
			}
			// match the sub-resource
			request.setPath(mp, sp);
			Object o = rm.subResource.checkProcessing(request);
			request.setPath(myPath, subpath);
			return new Triple<>(rm, new Pair<>(mp, sp), o);
		}
		return null;
	}
	
	@SuppressWarnings("resource")
	@Override
	protected ISynchronizationPoint<?> processServiceRequest(Object fromCheck, WebRequest request) {
		if (fromCheck instanceof RESTSpecificationPlugin) {
			MemoryIO io = new MemoryIO(8192, "REST specification");
			OutputToInput o2i = new OutputToInput(io, "REST specification");
			request.getResponse().setStatus(200);
			request.getResponse().setRawContentType(((RESTSpecificationPlugin)fromCheck).getContentType());
			request.getResponse().getMIME().setBodyToSend(o2i);
			((RESTSpecificationPlugin)fromCheck).generate(this, o2i, request).listenInline(() -> { o2i.endOfData(); });
			return new SynchronizationPoint<>(true);
		}
		if (fromCheck instanceof Pair) {
			@SuppressWarnings("unchecked")
			Pair<RestMethod, String> p = (Pair<RestMethod, String>)fromCheck;
			RestMethod rm = p.getValue1();
			String resourceId = p.getValue2();
			return call(rm, resourceId, request, bundle);
		}
		// sub-resources
		@SuppressWarnings("unchecked")
		Triple<RestMethod, Pair<String, String>, Object> t = (Triple<RestMethod, Pair<String, String>, Object>)fromCheck;
		RestMethod rm = t.getValue1();
		String mp = t.getValue2().getValue1();
		String sp = t.getValue2().getValue2();
		Object o = t.getValue3();
		String myPath = request.getCurrentPath();
		String subPath = request.getSubPath();
		request.setPath(mp, sp);
		ISynchronizationPoint<?> res = rm.subResource.process(o, request);
		SynchronizationPoint<Exception> result = new SynchronizationPoint<>();
		res.listenInline(() -> {
			request.setPath(myPath, subPath);
			res.listenInlineSP(result);
		});
		result.forwardCancel(res);
		return result;
	}
	
	private boolean needsAuthentication(RestMethod rm) {
		if (globalRoles.length > 0) return true;
		if (globalBooleanRights.length > 0) return true;
		if (globalIntegerRights.length > 0) return true;
		if (rm.specificRoles.length > 0) return true;
		if (rm.specificBooleanRights.length > 0) return true;
		if (rm.specificIntegerRights.length > 0) return true;
		return false;
	}
	
	private ISynchronizationPoint<?> call(RestMethod rm, String resourceId, WebRequest request, WebResourcesBundle bundle) {
		if (!needsAuthentication(rm))
			return call(rm, resourceId, request, bundle, null);
		if (authenticationProvider == null) {
			internalError("No authentication provider, but authentication needed by this service", request);
			return new SynchronizationPoint<>(true);
		}
		if (rightsManager == null) {
			internalError("No rights manager, but authentication needed by this service", request);
			return new SynchronizationPoint<>(true);
		}
		AsyncWork<IAuthentication, Exception> auth = request.authenticate(authenticationProvider);
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		auth.listenAsync(new Task.Cpu<Void, NoException>("Processing REST request", Task.PRIORITY_NORMAL) {
			@Override
			public Void run() {
				if (sp.isCancelled()) return null;
				if (auth.hasError()) {
					logger.error("Authentication error", auth.getError());
					forbidden(request);
					sp.unblock();
					return null;
				}
				ISynchronizationPoint<? extends Exception> c = call(rm, resourceId, request, bundle, auth.getResult());
				sp.forwardCancel(c);
				c.listenInlineSP(sp);
				return null;
			}
		}, true);
		return sp;
	}
	
	private ISynchronizationPoint<?> call(RestMethod rm, String resourceId, WebRequest request, WebResourcesBundle bundle, IAuthentication auth) {
		try {
			for (WebService.RequireRole role : globalRoles)
				if (auth == null || !rightsManager.hasRole(auth, role.value())) {
					forbidden(request);
					return new SynchronizationPoint<>(true);
				}
			for (WebService.RequireRole role : rm.specificRoles)
				if (auth == null || !rightsManager.hasRole(auth, role.value())) {
					forbidden(request);
					return new SynchronizationPoint<>(true);
				}
			for (WebService.RequireBooleanRight right : globalBooleanRights)
				if (auth == null || !rightsManager.hasRight(auth, right.name(), right.value())) {
					forbidden(request);
					return new SynchronizationPoint<>(true);
				}
			for (WebService.RequireBooleanRight right : rm.specificBooleanRights)
				if (auth == null || !rightsManager.hasRight(auth, right.name(), right.value())) {
					forbidden(request);
					return new SynchronizationPoint<>(true);
				}
			for (WebService.RequireIntegerRight right : globalIntegerRights)
				if (auth == null || !rightsManager.hasRight(auth, right.name(), new FragmentedRangeLong(new RangeLong(right.value(), right.value())))) {
					forbidden(request);
					return new SynchronizationPoint<>(true);
				}
			for (WebService.RequireIntegerRight right : rm.specificIntegerRights)
				if (auth == null || !rightsManager.hasRight(auth, right.name(), new FragmentedRangeLong(new RangeLong(right.value(), right.value())))) {
					forbidden(request);
					return new SynchronizationPoint<>(true);
				}
			
			Parameter[] paramsDef = rm.method.getParameters();
			Object[] params = new Object[paramsDef.length];
			int bodyIndex = -1;
			for (int i = 0; i < paramsDef.length; ++i) {
				Class<?> type = paramsDef[i].getType();
				// special parameters
				if (type.isAssignableFrom(WebRequest.class))
					params[i] = request;
				else if (type.isAssignableFrom(HTTPRequest.class))
					params[i] = request.getRequest();
				else if (type.isAssignableFrom(HTTPServerResponse.class))
					params[i] = request.getResponse();
				else if (type.isAssignableFrom(Session.class))
					params[i] = request.getSession(true);
				else if (type.isAssignableFrom(TCPServerClient.class))
					params[i] = request.getClient();
				else if (type.isAssignableFrom(IAuthentication.class)) {
					if (auth == null) {
						if (authenticationProvider == null)
							throw new Exception("No authentication provider, but authentication needed by this service");
						AsyncWork<IAuthentication, Exception> a = request.authenticate(authenticationProvider);
						SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
						a.listenAsync(new Task.Cpu<Void, NoException>("Processing REST request", Task.PRIORITY_NORMAL) {
							@Override
							public Void run() {
								if (sp.isCancelled()) return null;
								if (a.hasError()) {
									logger.error("Authentication error", a.getError());
									forbidden(request);
									sp.unblock();
									return null;
								}
								ISynchronizationPoint<? extends Exception> c = call(rm, resourceId, request, bundle, a.getResult());
								sp.forwardCancel(c);
								c.listenInlineSP(sp);
								return null;
							}
						}, true);
						return sp;
					}
					params[i] = auth;
				} else {
					REST.Id id = paramsDef[i].getAnnotation(REST.Id.class);
					if (id != null) {
						if (resourceId == null)
							params[i] = null;
						else {
							try {
								if (byte.class.equals(type) || Byte.class.equals(type))
									params[i] = new Byte(resourceId);
								else if (short.class.equals(type) || Short.class.equals(type))
									params[i] = new Short(resourceId);
								else if (int.class.equals(type) || Integer.class.equals(type))
									params[i] = new Integer(resourceId);
								else if (long.class.equals(type) || Long.class.equals(type))
									params[i] = new Long(resourceId);
								else if (BigInteger.class.equals(type))
									params[i] = new BigInteger(resourceId);
								else if (String.class.equals(type))
									params[i] = resourceId;
								else
									throw new Exception("Unsupported resource id type "+type.getName());
							} catch (NumberFormatException e) {
								throw new HTTPResponseError(400, "Invalid resource id: "+resourceId);
							}
						}
						continue;
					}
					WebService.Query q = paramsDef[i].getAnnotation(WebService.Query.class);
					WebService.Body b = paramsDef[i].getAnnotation(WebService.Body.class);
					if (q == null && b == null)
						throw new Exception("Parameter "+paramsDef[i].getName()+" must have the @Body or @Query annotation");
					if (q != null && b != null)
						throw new Exception("Parameter "+paramsDef[i].getName()+" cannot have both annotations @Body and @Query");
					
					if (q != null) {
						params[i] = WebServiceUtils.fillFromParameter(q.name(), type, request.getRequest());
						if (params[i] == null && q.required())
							throw new HTTPResponseError(400, "Missing required parameter " + q.name());
					} else {
						if (bodyIndex != -1)
							throw new Exception("Only one parameter can have the @Body annotation in method "+rm.method.getName());
						bodyIndex = i;
					}
				}
			}
			
			SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
			Task<Void,NoException> execute = new Task.Cpu.FromRunnable("Execute REST method " + rm.method.getDeclaringClass().getName() + '.' + rm.method.getName(), Task.PRIORITY_NORMAL, () -> {
				if (sp.isCancelled()) return;
				try {
					Object result;
					try { result = rm.method.invoke(service, params); }
					catch (InvocationTargetException e) {
						Throwable err = e.getTargetException();
						logger.error("Error calling REST method " + rm.method.getName() + " on class " + rm.method.getDeclaringClass().getName(), err);
						if (err instanceof HTTPResponseError)
							error(((HTTPResponseError)err).getStatusCode(), e.getMessage(), request);
						else
							internalError(err.getMessage(), request);
						sp.unblock();
						return;
					}
					catch (Throwable t) {
						logger.error("Error calling REST method " + rm.method.getName() + " on class " + rm.method.getDeclaringClass().getName(), t);
						internalError(t.getMessage(), request);
						sp.unblock();
						return;
					}
					if (sp.isCancelled()) return;
					if (result instanceof AsyncWork) {
						AsyncWork<?,?> processing = (AsyncWork<?,?>)result;
						sp.forwardCancel(processing);
						processing.listenAsync(new Task.Cpu<Void,NoException>("Sending REST response", Task.PRIORITY_NORMAL) {
							@Override
							public Void run() {
								if (sp.isCancelled()) return null;
								if (!processing.isSuccessful()) {
									Exception error = processing.hasError() ? processing.getError() : processing.getCancelEvent();
									logger.error("Error processing REST method " + rm.method.getName() + " on class " + rm.method.getDeclaringClass().getName(), error);
									internalError(error.getMessage(), request);
									sp.unblock();
								} else {
									TypeDefinition expectedType = null;
									Type t = rm.method.getGenericReturnType();
									if (t instanceof ParameterizedType) {
										Type[] types = ((ParameterizedType)t).getActualTypeArguments();
										if (types.length == 2)
											expectedType = new TypeDefinition(new TypeDefinition(rm.method.getDeclaringClass()), types[0]);
									}
									success(processing.getResult(), expectedType, request, bundle, sp);
								}
								return null;
							}
						}, true);
						return;
					}
					success(result, new TypeDefinition(new TypeDefinition(rm.method.getDeclaringClass()), rm.method.getGenericReturnType()), request, bundle, sp);
				} catch (Throwable t) {
					logger.error("Error processing REST method " + rm.method.getName() + " on class " + rm.method.getDeclaringClass().getName(), t);
					internalError(t.getMessage(), request);
					sp.unblock();
				}
			});
			
			if (bodyIndex == -1) {
				execute.start();
				return sp;
			}
			Type t = paramsDef[bodyIndex].getParameterizedType();
			AsyncWork<?, Exception> body = WebServiceUtils.fillFromBody(paramsDef[bodyIndex].getType(), t instanceof ParameterizedType ? (ParameterizedType)t : null, request.getRequest(), bundle);
			if (body.isUnblocked()) {
				if (body.hasError()) {
					logger.error("Error reading REST Body parameter", body.getError());
					internalError(body.getError().getMessage(), request);
					sp.unblock();
					return sp;
				}
				params[bodyIndex] = body.getResult();
				execute.start();
				return sp;
			}
			int index = bodyIndex;
			body.listenInline(() -> {
				if (body.hasError()) {
					logger.error("Error reading REST Body parameter", body.getError());
					internalError(body.getError().getMessage(), request);
					sp.unblock();
					return;
				}
				params[index] = body.getResult();
				execute.start();
			});
			
			return sp;
		} catch (HTTPResponseError e) {
			error(e.getStatusCode(), e.getMessage(), request);
			return new SynchronizationPoint<>(true);
		} catch (Throwable t) {
			logger.error("Error processing REST request", t);
			internalError(t.getMessage(), request);
			return new SynchronizationPoint<>(true);
		}
	}
	
	@SuppressWarnings("resource")
	private void success(Object result, TypeDefinition expectedType, WebRequest request, WebResourcesBundle bundle, SynchronizationPoint<Exception> sp) {
		if (result == null && (Void.class.equals(expectedType.getBase()) || void.class.equals(expectedType.getBase()))) {
			// nothing returned
			request.getResponse().setStatus(200);
			sp.unblock();
			return;
		}
		
		if (MimeMessage.class.isAssignableFrom(expectedType.getBase())) {
			// raw result
			MimeMessage mime = (MimeMessage)result;
			for (MimeHeader h : mime.getHeaders())
				request.getResponse().getMIME().addHeader(h);
			request.getResponse().getMIME().setBodyToSend(mime.getBodyToSend());
			request.getResponse().setStatus(200);
			HTTPServerProtocol.handleRangeRequest(request.getRequest(), request.getResponse());
			sp.unblock();
			return;
		}
		
		String responseType = null;
		Serializer ser = null;
		
		ParameterizedHeaderValues accept = new ParameterizedHeaderValues();
		for (MimeHeader h : request.getRequest().getMIME().getHeaders("Accept")) {
			try {
				ParameterizedHeaderValues list = h.getValue(ParameterizedHeaderValues.class);
				accept.getValues().addAll(list.getValues());
			} catch (Throwable t) {
				// ignore
			}
		}
		for (ParameterizedHeaderValue acceptType : accept.getValues()) {
			String type = acceptType.getMainValue();
			ser = bundle.getSerializer(type, StandardCharsets.UTF_8, expectedType.getBase());
			if (ser != null) {
				responseType = type + "; charset=utf-8";
				break;
			}
		}
		if (ser == null) {
			ParameterizedHeaderValue ct = null;
			try { ct = request.getRequest().getMIME().getContentType(); }
			catch (Throwable t) { /* ignore */ }
			if (ct == null) {
				responseType = "application/json; charset=utf-8";
				ser = new JSONSerializer(StandardCharsets.UTF_8, 4096, false);
			} else {
				String cs = ct.getParameterIgnoreCase("charset");
				Charset charset = null;
				if (cs != null)
					try { charset = Charset.forName(cs); }
					catch (Throwable t) { /* ignore */ }
				if (charset == null) charset = StandardCharsets.UTF_8;
				ser = bundle.getSerializer(ct.getMainValue(), charset, expectedType.getBase());
				if (ser != null)
					responseType = ct.getMainValue() + "; charset=" + charset.name();
			}
		}
		
		if (ser == null) {
			error(HttpURLConnection.HTTP_NOT_ACCEPTABLE, "Content type " + responseType + " is not supported for the response", request);
			sp.unblock();
			return;
		}
		
		if (sp.isCancelled()) return;
		
		OutputToInputBuffers data = new OutputToInputBuffers(true, 10, Task.PRIORITY_NORMAL);
		ISynchronizationPoint<Exception> serialization = ser.serialize(result, expectedType, data, bundle.getSerializationRules());
		serialization.listenInline(new Runnable() {
			@Override
			public void run() {
				if (serialization.hasError()) {
					logger.error("Error generating REST response", serialization.getError());
				}
				data.endOfData();
			}
		});
		request.getResponse().setStatus(200);
		request.getResponse().setRawContentType(responseType);
		request.getResponse().getMIME().setBodyToSend(data);
		sp.forwardCancel(serialization);
		// wait for some data to be ready before to send the response
		data.canStartReading().listenInlineSP(sp);
	}
	
	@SuppressWarnings("resource")
	private static void error(int code, String message, WebRequest request) {
		try {
			request.getResponse().setStatus(code);
			if (message != null) {
				request.getResponse().setRawContentType("text/plain;charset=utf-8");
				request.getResponse().getMIME().setBodyToSend(new ByteArrayIO(message.getBytes(StandardCharsets.UTF_8), "Error message"));
			}
		} catch (Throwable t) { /* ignore */ }
	}
	
	private static void forbidden(WebRequest request) {
		error(HttpURLConnection.HTTP_FORBIDDEN, "Access denied", request);
	}
	
	private static void internalError(String message, WebRequest request) {
		error(HttpURLConnection.HTTP_INTERNAL_ERROR, message, request);
	}
	
}
