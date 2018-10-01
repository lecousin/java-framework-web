package net.lecousin.framework.web.services.soap;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.Inject;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.buffering.SimpleBufferedWritable;
import net.lecousin.framework.io.out2in.OutputToInputBuffers;
import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.http.exception.HTTPError;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.network.http.server.HTTPServerResponse;
import net.lecousin.framework.network.mime.MimeUtil;
import net.lecousin.framework.network.mime.header.ParameterizedHeaderValue;
import net.lecousin.framework.network.server.TCPServerClient;
import net.lecousin.framework.network.session.Session;
import net.lecousin.framework.util.ClassUtil;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebResourcesBundle;
import net.lecousin.framework.web.security.IAuthentication;
import net.lecousin.framework.web.security.IAuthenticationProvider;
import net.lecousin.framework.web.security.IRightsManager;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.WebServiceProvider;
import net.lecousin.framework.web.services.WebServiceUtils;
import net.lecousin.framework.xml.XMLStreamEvents.ElementContext;
import net.lecousin.framework.xml.XMLStreamEvents.Event;
import net.lecousin.framework.xml.XMLStreamEventsRecorder;
import net.lecousin.framework.xml.XMLStreamReaderAsync;
import net.lecousin.framework.xml.XMLWriter;
import net.lecousin.framework.xml.dom.XMLDocument;
import net.lecousin.framework.xml.dom.XMLElement;
import net.lecousin.framework.xml.serialization.XMLDeserializer;

public class SOAPWebServiceProvider extends WebServiceProvider<SOAP> {
	
	public SOAPWebServiceProvider(WebResourcesBundle bundle, SOAP soap) throws Exception {
		super(bundle, soap);
		logger = LCCore.getApplication().getLoggerFactory().getLogger(SOAPWebServiceProvider.class);
		serviceDeclaration = soap.getClass().getAnnotation(SOAP.Service.class);
		if (serviceDeclaration == null)
			throw new Exception("Class "+soap.getClass().getName()+" must have the SOAP.Service annotation");
		
		for (Method m : soap.getClass().getDeclaredMethods()) {
			if ((m.getModifiers() & Modifier.PUBLIC) == 0) continue;
			SOAP.Operation op = m.getAnnotation(SOAP.Operation.class);
			if (op == null) continue;
			Operation o = new Operation();
			o.method = m;
			o.action = op.action();
			if (o.action.isEmpty()) o.action = m.getName();

			o.specificRoles = m.getAnnotationsByType(WebService.RequireRole.class);
			o.specificBooleanRights = m.getAnnotationsByType(WebService.RequireBooleanRight.class);
			o.specificIntegerRights = m.getAnnotationsByType(WebService.RequireIntegerRight.class);
			
			operations.put(o.action, o);
		}
		if (operations.isEmpty())
			throw new IllegalArgumentException("SOAP Service "+soap.getClass().getName()+" does not declare any operation");
		
		globalRoles = soap.getClass().getAnnotationsByType(WebService.RequireRole.class);
		globalBooleanRights = soap.getClass().getAnnotationsByType(WebService.RequireBooleanRight.class);
		globalIntegerRights = soap.getClass().getAnnotationsByType(WebService.RequireIntegerRight.class);
		
		for (SOAPFilter filter : bundle.getCustomFilters(SOAPFilter.class)) {
			for (Method m : filter.getClass().getDeclaredMethods()) {
				if ((m.getModifiers() & Modifier.PUBLIC) == 0) continue;
				SOAPFilter.PreFilter pre = m.getAnnotation(SOAPFilter.PreFilter.class);
				if (pre != null) {
					preFilters.add(new Pair<>(filter, m));
					continue;
				}
				SOAPFilter.PostFilter post = m.getAnnotation(SOAPFilter.PostFilter.class);
				if (post != null) {
					postFilters.add(new Pair<>(filter, m));
					continue;
				}
			}
		}
	}
	
	@Inject(required = false)
	private IAuthenticationProvider authenticationProvider;
	@Inject(required = false)
	private IRightsManager rightsManager;
	
	private final Logger logger;
	SOAP.Service serviceDeclaration;
	HashMap<String, Operation> operations = new HashMap<>();
	private WebService.RequireRole[] globalRoles;
	private WebService.RequireBooleanRight[] globalBooleanRights;
	private WebService.RequireIntegerRight[] globalIntegerRights;
	private List<Pair<SOAPFilter,Method>> preFilters = new ArrayList<>();
	private List<Pair<SOAPFilter,Method>> postFilters = new ArrayList<>();

	static class Operation {
		String action;
		Method method;
		WebService.RequireRole[] specificRoles;
		WebService.RequireBooleanRight[] specificBooleanRights;
		WebService.RequireIntegerRight[] specificIntegerRights;
	}
	
	@Override
	public String getServiceTypeName() {
		return "SOAP";
	}
	
	@Override
	public String getDefaultPath() {
		return serviceDeclaration.defaultPath();
	}

	@Override
	public List<OperationDescription> getOperations() {
		ArrayList<OperationDescription> list = new ArrayList<>(operations.size());
		for (Operation op : operations.values()) {
			WebService.Description d = op.method.getAnnotation(WebService.Description.class);
			String descr = d != null ? d.value() : "No description";
			list.add(new OperationDescription(op.action, descr));
		}
		return list;
	}
	
	@Override
	public List<WebServiceSpecification> getSpecifications() {
		ArrayList<WebServiceSpecification> list = new ArrayList<>();
		list.add(new WebServiceSpecification("WSDL", "wsdl"));
		return list;
	}

	@Override
	public Object checkProcessing(WebRequest request) {
		String action = request.getRequest().getMIME().getFirstHeaderRawValue("SOAPAction");
		if (action != null) {
			try { action = MimeUtil.decodeRFC2047(action); }
			catch (IOException e) {
				// ignore
			}
			action = action.trim();
			// this is an operation request
			// we expect it to be a POST and no sub-path
			if (!HTTPRequest.Method.POST.equals(request.getRequest().getMethod()))
				return null;
			if (request.getSubPath().length() > 0)
				return null;
			return operations.get(action);
		}
		// no soap action, this may be for documentation
		if (HTTPRequest.Method.GET.equals(request.getRequest().getMethod())) {
			String subpath = request.getSubPath();
			if ("wsdl".equalsIgnoreCase(subpath))
				return Boolean.TRUE;
			return null;
		}
		return null;
	}

	@SuppressWarnings("resource")
	@Override
	protected ISynchronizationPoint<?> processServiceRequest(Object fromCheck, WebRequest request) {
		if (!(fromCheck instanceof Operation))
			return processDocRequest(request);
		Operation op = (Operation)fromCheck;
		// start reading the body
		PreBufferedReadable input = new PreBufferedReadable(request.getRequest().getMIME().getBodyReceivedAsInput(), 8192, Task.PRIORITY_NORMAL, 4096, Task.PRIORITY_NORMAL, 16);
		// check authentication
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		processOperation(op, input, request, sp);
		return sp;
	}
	
	private void processOperation(Operation op, IO.Readable.Buffered input, WebRequest request, SynchronizationPoint<Exception> sp) {
		ParameterizedHeaderValue contentType;
		try { contentType = request.getRequest().getMIME().getContentType(); }
		catch (Throwable t) {
			error(400, "Invalid Content-Type: " + t.getMessage(), false, request);
			sp.unblock();
			return;
		}
		if (contentType.getMainValue().equals("application/soap+xml") || contentType.getMainValue().equals("text/xml")) {
			Charset encoding = null;
			String charset = contentType.getParameterIgnoreCase("charset");
			if (charset != null)
				try { encoding = Charset.forName(charset); }
				catch (Throwable t) {}
			XMLStreamReaderAsync xml = new XMLStreamReaderAsync(input, encoding, 8192);
			processOperation(op, xml, request, sp);
			return;
		}
		error(400, "Content-Type " + contentType.getMainValue() + " not supported", false, request);
		sp.unblock();
	}
	
	private class ParseSOAPRequest extends Task.Cpu<Void, NoException> {
		public ParseSOAPRequest(ISynchronizationPoint<Exception> wait, SynchronizationPoint<Exception> sp, WebRequest request, Runnable run) {
			super("Parsing SOAP message", Task.PRIORITY_NORMAL);
			this.wait = wait;
			this.sp = sp;
			this.request = request;
			this.run = run;
		}
		
		protected ISynchronizationPoint<Exception> wait;
		protected SynchronizationPoint<Exception> sp;
		protected WebRequest request;
		protected Runnable run;
		
		@Override
		public Void run() {
			if (wait.hasError()) {
				error(400, wait.getError().getMessage(), false, request);
				logger.error("Error parsing SOAP message", wait.getError());
				sp.unblock();
				return null;
			}
			run.run();
			return null;
		}
	}
	
	private void processOperation(Operation op, XMLStreamReaderAsync xml, WebRequest request, SynchronizationPoint<Exception> sp) {
		if (sp.isCancelled()) return;
		ISynchronizationPoint<Exception> start = xml.startRootElement();
		start.listenAsync(new ParseSOAPRequest(start, sp, request, () -> {
			if (start.hasError()) {
				error(400, start.getError().getMessage(), false, request);
				logger.error("Error parsing SOAP message", start.getError());
				sp.unblock();
				return;
			}
			if (!xml.event.localName.equals("Envelope") || !xml.getNamespaceURI(xml.event.namespacePrefix).equals(SOAPUtil.SOAP_NS)) {
				error(400, "Root element must be an Envelope in namespace " + SOAPUtil.SOAP_NS +
					", found is " + xml.event.localName + " in namespace "
					+ xml.getNamespaceURI(xml.event.namespacePrefix), false, request);
				sp.unblock();
				return;
			}
			processEnvelope(op, xml, request, sp);
		}), true);
	}
	
	private void processEnvelope(Operation op, XMLStreamReaderAsync xml, WebRequest request, SynchronizationPoint<Exception> sp) {
		if (sp.isCancelled()) return;
		ElementContext elem = xml.event.context.getFirst();
		ISynchronizationPoint<Exception> next = xml.nextInnerElement(elem);
		if (next.isUnblocked()) {
			if (next.hasError()) {
				error(400, next.getError().getMessage(), false, request);
				logger.error("Error parsing SOAP message", next.getError());
				sp.unblock();
				return;
			}
			if (xml.event.localName.equals("Header")) {
				processHeader(op, xml, request, sp, new LinkedList<>());
				return;
			}
			if (xml.event.localName.equals("Body")) {
				processPreFilters(op, xml, request, sp, new LinkedList<>(), 0);
				return;
			}
			error(400, "Unexpected element " + xml.event.localName + ", Header or Body expected", false, request);
			sp.unblock();
			return;
		}
		next.listenAsync(new ParseSOAPRequest(next, sp, request, () -> {
			if (xml.event.localName.equals("Header")) {
				processHeader(op, xml, request, sp, new LinkedList<>());
				return;
			}
			if (xml.event.localName.equals("Body")) {
				processPreFilters(op, xml, request, sp, new LinkedList<>(), 0);
				return;
			}
			error(400, "Unexpected element " + xml.event.localName + ", Header or Body expected", false, request);
			sp.unblock();
		}), true);
	}
	
	private void processHeader(Operation op, XMLStreamReaderAsync xml, WebRequest request, SynchronizationPoint<Exception> sp, LinkedList<XMLStreamEventsRecorder.Async> headers) {
		if (sp.isCancelled()) return;
		if (xml.event.isClosed) {
			// Header closed
			goToBody(op, xml, request, sp, headers);
			return;
		}
		do {
			ISynchronizationPoint<Exception> next = xml.next();
			if (sp.isCancelled()) return;
			if (next.isUnblocked()) {
				if (next.hasError()) {
					error(400, next.getError().getMessage(), false, request);
					logger.error("Error parsing SOAP message", next.getError());
					sp.unblock();
					return;
				}
				if (Event.Type.END_ELEMENT.equals(xml.event.type)) {
					goToBody(op, xml, request, sp, headers);
					return;
				}
				if (Event.Type.START_ELEMENT.equals(xml.event.type)) {
					XMLStreamEventsRecorder.Async recorder = new XMLStreamEventsRecorder.Async(xml);
					recorder.startRecording(true);
					ISynchronizationPoint<Exception> close = recorder.closeElement();
					if (close.isUnblocked()) {
						if (close.hasError()) {
							error(400, close.getError().getMessage(), false, request);
							logger.error("Error parsing SOAP message", close.getError());
							sp.unblock();
							return;
						}
						recorder.stopRecording();
						headers.add(recorder);
						continue;
					}
					close.listenAsync(new ParseSOAPRequest(close, sp, request, () -> {
						recorder.stopRecording();
						headers.add(recorder);
						processHeader(op, xml, request, sp, headers);
					}), true);
					return;
				}
				continue;
			}
			next.listenAsync(new ParseSOAPRequest(next, sp, request, () -> {
				if (Event.Type.END_ELEMENT.equals(xml.event.type)) {
					goToBody(op, xml, request, sp, headers);
					return;
				}
				if (Event.Type.START_ELEMENT.equals(xml.event.type)) {
					XMLStreamEventsRecorder.Async recorder = new XMLStreamEventsRecorder.Async(xml);
					recorder.startRecording(true);
					ISynchronizationPoint<Exception> close = recorder.closeElement();
					if (close.isUnblocked()) {
						if (close.hasError()) {
							error(400, close.getError().getMessage(), false, request);
							logger.error("Error parsing SOAP message", close.getError());
							sp.unblock();
							return;
						}
						recorder.stopRecording();
						headers.add(recorder);
						processHeader(op, xml, request, sp, headers);
						return;
					}
					close.listenAsync(new ParseSOAPRequest(close, sp, request, () -> {
						recorder.stopRecording();
						headers.add(recorder);
						processHeader(op, xml, request, sp, headers);
					}), true);
					return;
				}
				processHeader(op, xml, request, sp, headers);
			}), true);
			return;
		} while (true);
	}
	
	private void goToBody(Operation op, XMLStreamReaderAsync xml, WebRequest request, SynchronizationPoint<Exception> sp, LinkedList<XMLStreamEventsRecorder.Async> headers) {
		ISynchronizationPoint<Exception> next = xml.nextStartElement();
		if (next.isUnblocked()) {
			if (next.hasError()) {
				error(400, next.getError().getMessage(), false, request);
				logger.error("Error parsing SOAP message", next.getError());
				sp.unblock();
				return;
			}
			if (!xml.event.localName.equals("Body")) {
				error(400, "Unexpected element " + xml.event.localName + ", Body expected", false, request);
				sp.unblock();
				return;
			}
			processPreFilters(op, xml, request, sp, headers, 0);
			return;
		}
		next.listenAsync(new ParseSOAPRequest(next, sp, request, () -> {
			if (!xml.event.localName.equals("Body")) {
				error(400, "Unexpected element " + xml.event.localName + ", Body expected", false, request);
				sp.unblock();
			} else
				processPreFilters(op, xml, request, sp, headers, 0);
		}), true);
	}
	
	private static class MethodCall {
		private Method method;
		private Object[] params;
		
		private int authIndex = -1;
		
		private int bodyIndex = -1;
		private Field bodyField = null;
	}
	
	private void processPreFilters(Operation op, XMLStreamReaderAsync xml, WebRequest request, SynchronizationPoint<Exception> sp, LinkedList<XMLStreamEventsRecorder.Async> headers, int preFilterIndex) {
		if (sp.isCancelled()) return;

		if (preFilterIndex >= preFilters.size()) {
			callOperation(op, xml, request, sp, headers);
			return;
		}
		
		Pair<SOAPFilter, Method> filter = preFilters.get(preFilterIndex);
		try {
			MethodCall call = prepareCall(filter.getValue2(), request, headers);
			if (call.bodyIndex != -1)
				throw new HTTPError(500, "SOAP PreFilter method " + call.method.getName() + " on class " + filter.getValue1().getClass().getName() + " declares a parameter for the Body, but a filter cannot use the SOAP message body");
			call.method.invoke(filter.getValue1(), call.params);
			// TODO if return a synchronization point, wit for it
			processPreFilters(op, xml, request, sp, headers, preFilterIndex + 1);
		} catch (HTTPError e) {
			error(e.getStatusCode(), e.getMessage(), e.getStatusCode() / 100 == 5, request);
			sp.unblock();
			return;
		} catch (Throwable t) {
			logger.error("Error calling SOAP PreFilter", t);
			error(500, "Internal error", true, request);
			sp.unblock();
			return;
		}
	}
	
	private MethodCall prepareCall(Method method, WebRequest request, LinkedList<XMLStreamEventsRecorder.Async> headers) throws Exception {
		MethodCall call = new MethodCall();
		call.method = method;
		
		java.lang.reflect.Parameter[] paramsDef = method.getParameters();
		call.params = new Object[paramsDef.length];
		for (int i = 0; i < paramsDef.length; ++i) {
			Class<?> type = paramsDef[i].getType();
			// special parameters
			if (type.isAssignableFrom(WebRequest.class))
				call.params[i] = request;
			else if (type.isAssignableFrom(HTTPRequest.class))
				call.params[i] = request.getRequest();
			else if (type.isAssignableFrom(HTTPServerResponse.class))
				call.params[i] = request.getResponse();
			else if (type.isAssignableFrom(Session.class))
				call.params[i] = request.getSession(true);
			else if (type.isAssignableFrom(TCPServerClient.class))
				call.params[i] = request.getClient();
			else if (type.isAssignableFrom(IAuthentication.class))
				call.authIndex = i;
			else {
				WebService.Body b = paramsDef[i].getAnnotation(WebService.Body.class);
				if (b != null) {
					if (call.bodyIndex != -1)
						throw new HTTPError(500, "Method " + method.getName() + " in class " + method.getDeclaringClass().getName() + " specifies several body parameters");
					call.bodyIndex = i;
					continue;
				}
				SOAP.BodyElement be = paramsDef[i].getAnnotation(SOAP.BodyElement.class);
				if (be != null) {
					if (call.bodyIndex != -1)
						throw new HTTPError(500, "Method " + method.getName() + " in class " + method.getDeclaringClass().getName() + " specifies several body parameters");
					if (!paramsDef[i].getType().isAssignableFrom(XMLElement.class))
						throw new HTTPError(500, "Method " + method.getName() + " in class " + method.getDeclaringClass().getName() + " specifies a BodyElement parameter with a wrong type, it must be a XMLElement");
					call.bodyIndex = i;
					continue;
				}
				SOAP.Header h = paramsDef[i].getAnnotation(SOAP.Header.class);
				if (h != null) {
					call.params[i] = getHeader(h, type, paramsDef[i].getParameterizedType(), request, headers);
					continue;
				}
				WebService.Query q = paramsDef[i].getAnnotation(WebService.Query.class);
				if (q != null) {
					call.params[i] = WebServiceUtils.fillFromParameter(q.name(), type, request.getRequest());
					continue;
				}
				SOAP.Message msg = paramsDef[i].getType().getAnnotation(SOAP.Message.class);
				if (msg != null) {
					call.params[i] = paramsDef[i].getType().newInstance();
					for (Field f : ClassUtil.getAllFields(paramsDef[i].getType())) {
						if (f.getAnnotation(SOAP.MessageBody.class) != null) {
							if (call.bodyIndex != -1)
								throw new HTTPError(500, "Method " + method.getName() + " in class " + method.getDeclaringClass().getName() + " specifies several body parameters");
							call.bodyField = f;
							call.bodyIndex = i;
							continue;
						}
						h = f.getAnnotation(SOAP.Header.class);
						if (h != null) {
							f.set(call.params[i], getHeader(h, f.getType(), f.getGenericType(), request, headers));
							continue;
						}
					}
				}
			}
		}
		return call;
	}
	
	private void callOperation(Operation op, XMLStreamReaderAsync xml, WebRequest request, SynchronizationPoint<Exception> sp, LinkedList<XMLStreamEventsRecorder.Async> headers) {
		MethodCall call;
		try {
			call = prepareCall(op.method, request, headers);
		} catch (HTTPError e) {
			error(e.getStatusCode(), e.getMessage(), e.getStatusCode() / 100 == 5, request);
			sp.unblock();
			return;
		} catch (Throwable t) {
			logger.error("Error calling SOAP PreFilter", t);
			error(500, "Internal error", true, request);
			sp.unblock();
			return;
		}
		
		if (globalRoles.length > 0 ||
			globalBooleanRights.length > 0 ||
			globalIntegerRights.length > 0 ||
			op.specificRoles.length > 0 ||
			op.specificBooleanRights.length > 0 ||
			op.specificIntegerRights.length > 0 ||
			call.authIndex != -1) {
			
			if (authenticationProvider == null) {
				internalError("No authentication provider, but authentication needed by this service", request);
				sp.unblock();
				return;
			}
			if (rightsManager == null) {
				internalError("No rights manager, but authentication needed by this service", request);
				sp.unblock();
				return;
			}
			AsyncWork<IAuthentication, Exception> auth = request.authenticate(authenticationProvider);
			if (auth.isUnblocked()) {
				if (auth.hasError()) {
					forbidden(request);
					sp.unblock();
					return;
				}
				callOperation(op, call, auth.getResult(), xml, request, sp, headers);
				return;
			}
			auth.listenAsync(new Task.Cpu<Void, NoException>("Process SOAP Request", Task.PRIORITY_NORMAL) {
				@Override
				public Void run() {
					if (sp.isCancelled()) return null;
					if (auth.hasError()) {
						forbidden(request);
						sp.unblock();
						return null;
					}
					callOperation(op, call, auth.getResult(), xml, request, sp, headers);
					return null;
				}
			}, true);
			return;
		}
		
		callOperation(op, call, null, xml, request, sp, headers);
	}

	private void callOperation(Operation op, MethodCall call, IAuthentication auth, XMLStreamReaderAsync xml, WebRequest request, SynchronizationPoint<Exception> sp, LinkedList<XMLStreamEventsRecorder.Async> headers) {
		for (WebService.RequireRole role : globalRoles)
			if (auth == null || !rightsManager.hasRole(auth, role.value())) {
				forbidden(request);
				sp.unblock();
				return;
			}
		for (WebService.RequireRole role : op.specificRoles)
			if (auth == null || !rightsManager.hasRole(auth, role.value())) {
				forbidden(request);
				sp.unblock();
				return;
			}
		for (WebService.RequireBooleanRight right : globalBooleanRights)
			if (auth == null || !rightsManager.hasRight(auth, right.name(), right.value())) {
				forbidden(request);
				sp.unblock();
				return;
			}
		for (WebService.RequireBooleanRight right : op.specificBooleanRights)
			if (auth == null || !rightsManager.hasRight(auth, right.name(), right.value())) {
				forbidden(request);
				sp.unblock();
				return;
			}
		for (WebService.RequireIntegerRight right : globalIntegerRights)
			if (auth == null || !rightsManager.hasRight(auth, right.name(), new FragmentedRangeLong(new RangeLong(right.value(), right.value())))) {
				forbidden(request);
				sp.unblock();
				return;
			}
		for (WebService.RequireIntegerRight right : op.specificIntegerRights)
			if (auth == null || !rightsManager.hasRight(auth, right.name(), new FragmentedRangeLong(new RangeLong(right.value(), right.value())))) {
				forbidden(request);
				sp.unblock();
				return;
			}
		
		if (call.authIndex != -1)
			call.params[call.authIndex] = auth;

		try {
			if (sp.isCancelled()) return;
			if (call.bodyIndex == -1) {
				callOperation(call, op, request, sp);
				return;
			}
			AsyncWork body;
			java.lang.reflect.Parameter[] paramsDef = call.method.getParameters();
			// we are on the envelope body element
			ElementContext envBody = xml.event.context.getFirst();
			AsyncWork<Boolean, Exception> next = xml.nextInnerElement(envBody);
			if (next.isUnblocked()) {
				if (next.hasError()) {
					error(400, "Invalid body: " + next.getError().getMessage(), false, request);
					sp.unblock();
					return;
				}
				if (!next.getResult().booleanValue()) {
					error(400, "Invalid body: no element inside", false, request);
					sp.unblock();
					return;
				}
				if ((call.bodyField != null && call.bodyField.getAnnotation(SOAP.BodyElement.class) != null) ||
					(call.bodyField == null && paramsDef[call.bodyIndex].getAnnotation(SOAP.BodyElement.class) != null)) {
					body = XMLElement.create(new XMLDocument(), xml);
				} else {
					body = new XMLDeserializer(
						xml,
						serviceDeclaration.targetNamespace() + "Request",
						paramsDef[call.bodyIndex].getType().getSimpleName()
					).deserializeValue(null, new TypeDefinition(null, call.bodyField != null ? call.bodyField.getGenericType() : paramsDef[call.bodyIndex].getParameterizedType()), "SOAPBody", bundle.getSerializationRules());
				}
			} else {
				body = new AsyncWork<>();
				next.listenAsync(new Task.Cpu<Void, NoException>("Parsing SOAP Body", Task.PRIORITY_NORMAL) {
					@SuppressWarnings("unchecked")
					@Override
					public Void run() {
						if (next.hasError()) {
							error(400, "Invalid body: " + next.getError().getMessage(), false, request);
							sp.unblock();
							return null;
						}
						if (!next.getResult().booleanValue()) {
							error(400, "Invalid body: no element inside", false, request);
							sp.unblock();
							return null;
						}
						if ((call.bodyField != null && call.bodyField.getAnnotation(SOAP.BodyElement.class) != null) ||
							(call.bodyField == null && paramsDef[call.bodyIndex].getAnnotation(SOAP.BodyElement.class) != null)) {
							XMLElement.create(new XMLDocument(), xml).listenInline(body);
						} else {
							new XMLDeserializer(
								xml,
								serviceDeclaration.targetNamespace() + "Request",
								paramsDef[call.bodyIndex].getType().getSimpleName()
							).deserializeValue(null, new TypeDefinition(null, call.bodyField != null ? call.bodyField.getGenericType() : paramsDef[call.bodyIndex].getParameterizedType()), "SOAPBody", bundle.getSerializationRules())
							.listenInline(body);
						}
						return null;
					}
				}, true);
			}
			if (body.isUnblocked()) {
				if (body.hasError()) {
					error(400, "Invalid body: " + body.getError().getMessage(), false, request);
					sp.unblock();
					return;
				}
				if (call.bodyField != null)
					call.bodyField.set(call.params[call.bodyIndex], body.getResult());
				else
					call.params[call.bodyIndex] = body.getResult();
				callOperation(call, op, request, sp);
				return;
			}
			body.listenInline(() -> {
				if (body.hasError()) {
					error(400, "Invalid body: " + body.getError().getMessage(), false, request);
					sp.unblock();
					return;
				}
				if (call.bodyField != null)
					try { call.bodyField.set(call.params[call.bodyIndex], body.getResult()); }
					catch (Throwable t) {
						// TODO
						t.printStackTrace();
						internalError(t.getMessage(), request);
						sp.unblock();
						return;
					}
				else
					call.params[call.bodyIndex] = body.getResult();
				callOperation(call, op, request, sp);
			});
		} catch (Throwable t) {
			// TODO
			t.printStackTrace();
			internalError(t.getMessage(), request);
			sp.unblock();
			return;
		}
	}
	
	private Object getHeader(SOAP.Header h, Class<?> type, Type parameterizedType, WebRequest request, LinkedList<XMLStreamEventsRecorder.Async> headers) throws HTTPError {
		XMLStreamEventsRecorder.Async header = null;
		for (XMLStreamEventsRecorder.Async recorder : headers) {
			Event e = recorder.getFirstRecordedEvent();
			if (!e.localName.equals(h.localName())) continue;
			String namespaceURI = h.namespaceURI();
			if (namespaceURI.length() == 0) namespaceURI = serviceDeclaration.targetNamespace();
			if (!e.namespaceURI.equals(namespaceURI)) continue;
			header = recorder;
			break;
		}
		if (header == null) {
			if (h.required())
				throw new HTTPError(400, "Missing header " + h.localName());
			return null;
		}
		if (type.isAssignableFrom(org.w3c.dom.Element.class)) {
			AsyncWork<XMLElement, Exception> result = XMLElement.create(new XMLDocument(), header);
			// as we use a recorder, it should be synchronous
			try { result.blockThrow(0); }
			catch (Exception e) {
				throw new HTTPError(400, "Invalid header " + h.localName() + ": " + e.getMessage());
			}
			return result.getResult();
		}
		header.replay();
		AsyncWork<Object, Exception> result = new XMLDeserializer(header, h.namespaceURI(), h.localName()).deserializeValue(null, new TypeDefinition(null, parameterizedType), "SOAPBody", bundle.getSerializationRules());
		// as we use a recorder, it should be synchronous
		try { result.blockThrow(0); }
		catch (Exception e) {
			logger.error("Error deserializing SOAP header", e);
			throw new HTTPError(400, "Invalid header " + h.localName() + ": " + e.getMessage());
		}
		return result.getResult();
	}
		
	private void callOperation(MethodCall call, Operation op, WebRequest request, SynchronizationPoint<Exception> sp) {
		new Task.Cpu<Void, NoException>("Executing SOAP operation", Task.PRIORITY_NORMAL) {
			@Override
			public Void run() {
				if (sp.isCancelled()) return null;
				try {
					Object result;
					try { result = call.method.invoke(service, call.params); }
					catch (InvocationTargetException e) {
						e.printStackTrace(); // TODO
						Throwable err = e.getTargetException();
						if (err instanceof HTTPResponseError)
							error(((HTTPResponseError)err).getStatusCode(), e.getMessage(), true, request);
						else
							internalError(err.getMessage(), request);
						sp.unblock();
						return null;
					}
					catch (Throwable t) {
						t.printStackTrace(); // TODO
						internalError(t.getMessage(), request);
						sp.unblock();
						return null;
					}
					if (result instanceof AsyncWork) {
						AsyncWork<?,?> processing = (AsyncWork<?,?>)result;
						processing.listenAsync(new Task.Cpu<Void,NoException>("Sending SOAP response", Task.PRIORITY_NORMAL) {
							@Override
							public Void run() {
								if (!processing.isSuccessful()) {
									Exception error = processing.hasError() ? processing.getError() : processing.getCancelEvent();
									internalError(error.getMessage(), request);
									sp.unblock();
								} else {
									success(op, processing.getResult(), request, sp);
								}
								return null;
							}
						}, true);
						sp.forwardCancel(processing);
						return null;
					}
					success(op, result, request, sp);
					return null;
				} catch (Throwable t) {
					// TODO
					t.printStackTrace();
					internalError(t.getMessage(), request);
					sp.unblock();
					return null;
				}
			}
		}.start();
	}
	
	private ISynchronizationPoint<Exception> processDocRequest(WebRequest request) {
		String subpath = request.getSubPath();
		if ("wsdl".equalsIgnoreCase(subpath))
			return WSDLGenerator.generateWSDL(this, request);
		// should never be here
		return new SynchronizationPoint<>(true);
	}
	
	@SuppressWarnings("resource")
	private void success(Operation op, Object result, WebRequest request, SynchronizationPoint<Exception> sp) {
		if (sp.isCancelled()) return;
		// build the response object
		SOAPMessageContent response = new SOAPMessageContent();
		response.bodyNamespaceURI = serviceDeclaration.targetNamespace() + "Response";
		if (result != null) {
			if (result.getClass().getAnnotation(SOAP.Message.class) != null) {
				// The class declares headers and body
				try { SOAPUtil.createContentFromMessage(result, response, serviceDeclaration.targetNamespace()); }
				catch (Throwable e) {
					logger.error("Error creating SOAP message", e);
					internalError("Unable to create response message", request);
					sp.unblock();
					return;
				}
			} else {
				response.bodyContent = result;
				if (op.method.getAnnotatedReturnType().getAnnotation(SOAP.BodyElement.class) == null) {
					Class<?> outputType = op.method.getReturnType();
					if (AsyncWork.class.equals(outputType)) {
						Type t = op.method.getGenericReturnType();
						Type[] types = ((ParameterizedType)t).getActualTypeArguments();
						response.bodyType = new TypeDefinition(new TypeDefinition(op.method.getDeclaringClass()), types[0]);
					} else
						response.bodyType = new TypeDefinition(new TypeDefinition(op.method.getDeclaringClass()), op.method.getGenericReturnType());
					response.bodyLocalName = response.bodyType.getBase().getSimpleName();
				}
			}
		}
		
		// call post-filters
		for (Pair<SOAPFilter, Method> filter : postFilters) {
			try {
				Class<?>[] types = filter.getValue2().getParameterTypes();
				Object[] params = new Object[types.length];
				for (int i = 0; i < types.length; ++i) {
					if (types[i].isAssignableFrom(WebRequest.class))
						params[i] = request;
					else if (types[i].isAssignableFrom(HTTPRequest.class))
						params[i] = request.getRequest();
					else if (types[i].isAssignableFrom(HTTPResponse.class))
						params[i] = request.getResponse();
					else if (types[i].isAssignableFrom(Session.class))
						params[i] = request.getSession(false);
					else if (types[i].isAssignableFrom(TCPServerClient.class))
						params[i] = request.getClient();
					else if (types[i].isAssignableFrom(SOAPMessageContent.class))
						params[i] = response;
				}
				filter.getValue2().invoke(filter.getValue1(), params);
			} catch (Throwable t) {
				logger.error("Error in SOAP post-filter " + filter.getValue1(), t);
				internalError("Error in SOAP post-filter", request);
				sp.unblock();
			}
		}
		
		// we can send the response
		OutputToInputBuffers out = initSendMessage(200, request);
		if (sp.isCancelled()) return;
		sp.unblock();
		SOAPUtil.sendMessage(response, out, bundle.getSerializationRules());
	}
	
	@SuppressWarnings("resource")
	private void error(int code, String message, boolean serverError, WebRequest request) {
		OutputToInputBuffers out = initSendMessage(code, request);
		SimpleBufferedWritable bout = new SimpleBufferedWritable(out, 4096);
		XMLWriter writer = new XMLWriter(bout, StandardCharsets.UTF_8, true, false);
		SOAPUtil.openEnvelope(writer, null);
		SOAPUtil.openBody(writer);
		writer.openElement(SOAPUtil.SOAP_NS, "Fault", null);
		writer.openElement(SOAPUtil.SOAP_NS, "Code", null);
		writer.openElement(SOAPUtil.SOAP_NS, "Value", null);
		writer.addText("soap:" + (serverError ? "Server" : "Client"));
		writer.closeElement();
		writer.closeElement();
		if (message != null) {
			writer.openElement(SOAPUtil.SOAP_NS, "Reason", null);
			writer.openElement(SOAPUtil.SOAP_NS, "Text", null);
			writer.addText(message);
			writer.closeElement();
			writer.closeElement();
		}
		writer.closeElement();
		writer.closeElement();
		writer.closeElement();
		SOAPUtil.finalizeSending(out, bout, writer);
	}
	
	private void internalError(String message, WebRequest request) {
		error(500, message, true, request);
	}
	
	private void forbidden(WebRequest request) {
		error(403, "Access denied", false, request);
	}
	
	private static OutputToInputBuffers initSendMessage(int code, WebRequest request) {
		request.getResponse().setStatus(code);
		request.getResponse().setRawContentType("application/soap+xml; charset=utf-8");
		OutputToInputBuffers o2i = new OutputToInputBuffers(false, 4, Task.PRIORITY_NORMAL);
		request.getResponse().getMIME().setBodyToSend(o2i);
		return o2i;
	}
	
	
}
