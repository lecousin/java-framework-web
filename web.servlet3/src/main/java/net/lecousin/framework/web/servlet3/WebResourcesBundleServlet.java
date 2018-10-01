package net.lecousin.framework.web.servlet3;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.CollectionsUtil;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.Threading;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.injection.InjectionContext;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOFromOutputStream;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.network.http.HTTPRequest.Method;
import net.lecousin.framework.network.http.HTTPRequest.Protocol;
import net.lecousin.framework.network.http.exception.HTTPError;
import net.lecousin.framework.network.http.server.HTTPServerResponse;
import net.lecousin.framework.network.mime.MimeHeader;
import net.lecousin.framework.network.session.ISession;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebResourcesBundle;
import net.lecousin.framework.web.WebSessionProvider;

public class WebResourcesBundleServlet implements Servlet {

	public static final String ATTRIBUTE_HTTPSERVLETREQUEST = "javax.servlet.HttpServletRequest";
	
	protected Logger logger;
	protected ServletConfig config;
	protected InjectionContext injection;
	protected WebResourcesBundle bundle;

	@Override
	public void init(ServletConfig config) throws ServletException {
		this.logger = LCCore.getApplication().getLoggerFactory().getLogger(WebResourcesBundleServlet.class);
		this.config = config;
		bundle = new WebResourcesBundle(injection);
		String filename = config.getInitParameter("configFile");
		if (filename == null || filename.isEmpty())
			throw new ServletException("Missing parameter configFile to initialize WebResourcesBundleServlet");
		try { bundle.configure(filename); }
		catch (Exception e) {
			throw new ServletException("Error configuring WebResourcesBundle from " + filename, e);
		}
	}

	@Override
	public ServletConfig getServletConfig() {
		return config;
	}

	@Override
	public String getServletInfo() {
		return "";
	}

	@Override
	public void destroy() {
	}

	@Override
	public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
		if (!(servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse))
			throw new ServletException("non-HTTP request or response");

		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		HTTPRequest req = new HTTPRequest(Method.valueOf(request.getMethod()), request.getPathInfo());
		req.setQueryString(request.getQueryString());
		req.setProtocol(Protocol.from(request.getProtocol()));
		for (String name : CollectionsUtil.singleTimeIterable(request.getHeaderNames())) {
			for (String value : CollectionsUtil.singleTimeIterable(request.getHeaders(name)))
				req.getMIME().addHeaderRaw(name, value);
		}
		req.setAttribute(ATTRIBUTE_HTTPSERVLETREQUEST, request);
		
		HTTPServerResponse resp = new HTTPServerResponse();
		
		AsyncContext ctx = servletRequest.startAsync();
		WebRequest wr = new WebRequest(new RemoteBridge(request, ctx), req, resp, request.isSecure(), sessionProvider);
		Object check = bundle.checkProcessing(wr);
		if (check == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			resp.sent.unblock();
			ctx.complete();
			return;
		}
		new Task.Cpu.FromRunnable("Process HTTP Servlet Request", Task.PRIORITY_NORMAL, new Runnable() {
			@Override
			public void run() {
				ISynchronizationPoint<Exception> sp = bundle.process(check, wr);
				sp.listenAsync(new Task.Cpu.FromRunnable("Send HTTP Servlet Response", Task.PRIORITY_NORMAL, new Runnable() {
					@SuppressWarnings("resource")
					@Override
					public void run() {
						if (sp.hasError()) {
							Exception err = sp.getError();
							logger.error("Error processing request", err);
							try {
								if (err instanceof HTTPError) {
									HTTPError r = (HTTPError)err;
									response.sendError(r.getStatusCode(), r.getMessage());
								} else
									response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Error");
							} catch (Throwable t) { /* ignore */ }
							resp.sent.unblock();
							ctx.complete();
							return;
						}
						if (sp.isCancelled()) {
							resp.sent.unblock();
							ctx.complete();
							return;
						}
						response.setStatus(resp.getStatusCode());
						for (MimeHeader h : resp.getMIME().getHeaders())
							response.addHeader(h.getName(), h.getRawValue());
						IO.Readable body = resp.getMIME().getBodyToSend();
						if (body == null) {
							resp.sent.unblock();
							ctx.complete();
							return;
						}
						OutputStream out;
						try { out = response.getOutputStream(); }
						catch (Throwable t) {
							resp.sent.unblock();
							ctx.complete();
							return;
						}
						IO.Writable io = new IOFromOutputStream(out, "HTTP Response", Threading.getUnmanagedTaskManager(), Task.PRIORITY_NORMAL);
						IOUtil.copy(body, io, -1, true, null, 0).listenInline(() -> {
							resp.sent.unblock();
							ctx.complete();
						});
					}
				}), true);
			}
		}).start();
	}
	
	protected WebSessionProvider sessionProvider = new WebSessionProvider() {
		@Override
		public AsyncWork<ISession, NoException> getSession(WebRequest request, boolean openIfNeeded) {
			HttpServletRequest r = (HttpServletRequest)request.getRequest().getAttribute(ATTRIBUTE_HTTPSERVLETREQUEST);
			return new AsyncWork<>(new SessionBridge(r.getSession(openIfNeeded)), null);
		}
		
		@Override
		public void removeSession(WebRequest request, ISession session) {
			HttpServletRequest r = (HttpServletRequest)request.getRequest().getAttribute(ATTRIBUTE_HTTPSERVLETREQUEST);
			HttpSession s = r.getSession(false);
			if (s != null)
				s.invalidate();
		}
		
		@Override
		public void saveSession(WebRequest request, ISession session) {
			// automatically done
		}
	};

}
