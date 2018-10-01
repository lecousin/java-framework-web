package net.lecousin.framework.web.services.doc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.io.buffering.MemoryIO;
import net.lecousin.framework.io.out2in.OutputToInput;
import net.lecousin.framework.io.serialization.annotations.Transient;
import net.lecousin.framework.network.http.HTTPRequest.Method;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.UnprotectedStringBuffer;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestProcessor;
import net.lecousin.framework.web.WebResourcesBundle;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.WebServiceProvider;
import net.lecousin.framework.web.services.WebServiceProvider.OperationDescription;
import net.lecousin.framework.web.services.WebServiceProvider.WebServiceSpecification;

public class WebServicesDocumentation implements WebRequestProcessor {

	@Transient
	private WebRequestProcessor parent;
	
	@Override
	public WebRequestProcessor getParent() {
		return parent;
	}

	@Override
	public void setParent(WebRequestProcessor parent) {
		this.parent = parent;
	}

	@Override
	public Object checkProcessing(WebRequest request) {
		if (Method.GET.equals(request.getRequest().getMethod())) {
			// TODO
			return Boolean.TRUE;
		}
		return null;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		new SearchWebServices(request, sp).start();
		return sp;
	}
	
	private class SearchWebServices extends Task.Cpu<Void, NoException> {
		private SearchWebServices(WebRequest request, SynchronizationPoint<Exception> sp) {
			super("Search web services for documentation", Task.PRIORITY_NORMAL);
			this.request = request;
			this.sp = sp;
		}
		
		private WebRequest request;
		private SynchronizationPoint<Exception> sp;
		
		@SuppressWarnings("resource")
		@Override
		public Void run() {
			if (sp.isCancelled()) return null;
			WebRequestProcessor p = parent;
			while (p != null && !(p instanceof WebResourcesBundle))
				p = p.getParent();
			if (p == null) {
				request.getResponse().setStatus(200);
				request.getResponse().setRawContentType("text/html;charset=utf-8");
				request.getResponse().getMIME().setBodyToSend(LCCore.getApplication().getResource("net.lecousin.framework.web/services-doc/no-service.html", Task.PRIORITY_NORMAL));
				sp.unblock();
				return null;
			}
			LinkedList<Pair<String, WebServiceProvider<?>>> services = new LinkedList<>();
			searchServices((WebResourcesBundle)p, "", services);
			if (services.isEmpty()) {
				request.getResponse().setStatus(200);
				request.getResponse().setRawContentType("text/html;charset=utf-8");
				request.getResponse().getMIME().setBodyToSend(LCCore.getApplication().getResource("net.lecousin.framework.web/services-doc/no-service.html", Task.PRIORITY_NORMAL));
				sp.unblock();
				return null;
			}

			request.getResponse().setRawContentType("text/html;charset=utf-8");
			IO.Readable io = LCCore.getApplication().getResource("net.lecousin.framework.web/services-doc/services-template.html", Task.PRIORITY_NORMAL);
			AsyncWork<UnprotectedStringBuffer,IOException> read = IOUtil.readFullyAsString(io, StandardCharsets.UTF_8, Task.PRIORITY_NORMAL);
			read.listenInline(() -> { io.closeAsync(); });
			
			// list by service type
			Map<String, List<Pair<String, WebServiceProvider<?>>>> byType = new HashMap<>();
			for (Pair<String, WebServiceProvider<?>> proc : services) {
				WebServiceProvider<?> provider = proc.getValue2();
				String s = provider.getServiceTypeName();
				List<Pair<String, WebServiceProvider<?>>> list = byType.get(s);
				if (list == null) {
					list = new LinkedList<>();
					byType.put(s, list);
				}
				list.add(proc);
			}

			DocContext ctx = new DocContext();
			for (Map.Entry<String, List<Pair<String, WebServiceProvider<?>>>> type : byType.entrySet()) {
				DocContext typeCtx = ctx.addListElement("web-service-types");
				typeCtx.setVariable("web-service-type", type.getKey());
				for (Pair<String, WebServiceProvider<?>> proc : type.getValue()) {
					DocContext procCtx = typeCtx.addListElement("services");
					procCtx.setVariable("service-path", proc.getValue1());
					WebServiceProvider<?> provider = proc.getValue2();
					WebService service = provider.getWebService();
					WebService.Description descr = service.getClass().getAnnotation(WebService.Description.class);
					procCtx.setVariable("service-description", descr != null ? descr.value() : "No description");
					for (WebServiceSpecification spec : provider.getSpecifications()) {
						DocContext specCtx = procCtx.addListElement("service-specifications");
						specCtx.setVariable("specification-name", spec.getName());
						specCtx.setVariable("specification-url", proc.getValue1() + spec.getPath());
					}
					for (OperationDescription op : provider.getOperations()) {
						DocContext opCtx = procCtx.addListElement("service-operations");
						opCtx.setVariable("operation-name", op.getName());
						opCtx.setVariable("operation-description", op.getDescription());
					}
				}
			}
			new Task.Cpu.FromRunnable("Generate web services documentation", Task.PRIORITY_NORMAL, () -> {
				if (sp.isCancelled()) return;
				if (read.hasError()) {
					LCCore.getApplication().getDefaultLogger().error("Error reading web services doc template", read.getError());
					request.getResponse().setStatus(500);
					sp.unblock();
					return;
				}
				UnprotectedStringBuffer text = read.getResult();
				ctx.generate(text);
				MemoryIO out = new MemoryIO(4096, "web services doc");
				OutputToInput o2i = new OutputToInput(out, "web services doc");
				text.encode(StandardCharsets.UTF_8, o2i, Task.PRIORITY_NORMAL).listenInline(() -> { o2i.endOfData(); });
				request.getResponse().getMIME().setBodyToSend(o2i);
				request.getResponse().setStatus(200);
				sp.unblock();
			}).startOn(read, true);
			return null;
		}
		
		private void searchServices(WebResourcesBundle bundle, String path, LinkedList<Pair<String, WebServiceProvider<?>>> services) {
			for (Pair<String, WebRequestProcessor> p : bundle.getProcessors()) {
				if (p.getValue2() instanceof WebServiceProvider) {
					services.add(new Pair<>(path + p.getValue1(), (WebServiceProvider<?>)p.getValue2()));
				} else if (p.getValue2() instanceof WebResourcesBundle) {
					searchServices((WebResourcesBundle)p.getValue2(), path + p.getValue1(), services);
				}
			}
		}
	}

}
