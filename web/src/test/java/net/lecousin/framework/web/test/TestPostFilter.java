package net.lecousin.framework.web.test;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;

public class TestPostFilter implements WebRequestFilter {

	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		String value = request.getResponse().getMIME().getFirstHeaderRawValue("X-Pre-Filter-1");
		if (value != null)
			request.getResponse().getMIME().setHeaderRaw("X-Post-Filter-1", value);
		return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
	}
	
}
