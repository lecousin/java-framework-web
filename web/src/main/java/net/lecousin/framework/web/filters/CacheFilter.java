package net.lecousin.framework.web.filters;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.math.IntegerUnit.Unit;
import net.lecousin.framework.math.TimeUnit.Millisecond;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestFilter;

/**
 * Add HTTP headers to the response to specify the response can be cached or not.
 * If the maxAge is 0 or negative, the response must not be cached, else it can be
 * cached for the specified number of milliseconds.
 */
public class CacheFilter implements WebRequestFilter {

	@Unit(Millisecond.class)
	public long maxAge = -1;
	
	@Override
	public AsyncWork<FilterResult, Exception> filter(WebRequest request) {
		if (maxAge > 0)
			request.getResponse().publicCache(Long.valueOf(maxAge));
		else
			request.getResponse().noCache();
		return new AsyncWork<>(FilterResult.CONTINUE_PROCESSING, null);
	}

	
}
