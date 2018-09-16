package net.lecousin.framework.web.services.rest.impl;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.network.http.exception.HTTPResponseError;
import net.lecousin.framework.network.mime.MimeMessage;
import net.lecousin.framework.web.services.WebService;
import net.lecousin.framework.web.services.rest.REST;

@REST.Resource(type=REST.ResourceType.MULTIPLE, path="locale")
@WebService.Description("Provides access to localized properties.")
public class LocalizedPropertiesService implements REST {

	public static class Properties {
		
		public List<Namespace> namespaces = new LinkedList<>();
		
		public static class Namespace {
			public String name;
			public Collection<String> languages;
		}
	}
	
	@REST.ListResources
	public Properties get() {
		Properties result = new Properties();
		for (String ns : LCCore.getApplication().getLocalizedProperties().getDeclaredNamespaces()) {
			Properties.Namespace n = new Properties.Namespace();
			n.name = ns;
			n.languages = LCCore.getApplication().getLocalizedProperties().getNamespaceLanguages(ns);
			result.namespaces.add(n);
		}
		return result;
	}
	
	@REST.GetResource
	public AsyncWork<MimeMessage, Exception> getNamespace(@REST.Id String id) throws HTTPResponseError {
		int i = id.lastIndexOf('.');
		if (i <= 0) throw new HTTPResponseError(404, "URL must be <namespace>.<language>");
		String language = id.substring(i + 1);
		String ns = id.substring(0, i);
		AsyncWork<MimeMessage, Exception> result = new AsyncWork<>();
		AsyncWork<Map<String, String>, Exception> map = LCCore.getApplication().getLocalizedProperties().getNamespaceContent(ns, language.split("-"));
		map.listenAsync(new Task.Cpu.FromRunnable("LocalizedPropertiesService.getNamespace", Task.PRIORITY_NORMAL, () -> {
			StringBuilder s = new StringBuilder(4096);
			for (Map.Entry<String, String> e : map.getResult().entrySet())
				s.append(e.getKey()).append('=').append(e.getValue()).append('\n');
			MimeMessage response = new MimeMessage();
			response.addHeaderRaw("Content-Type", "text/plain;charset=utf-8");
			ByteArrayIO body = new ByteArrayIO(s.toString().getBytes(StandardCharsets.UTF_8), "response");
			response.setBodyToSend(body);
			result.unblockSuccess(response);
		}), result);
		return result;
	}
	
}
