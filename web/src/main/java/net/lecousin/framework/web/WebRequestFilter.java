package net.lecousin.framework.web;

import net.lecousin.framework.concurrent.synch.AsyncWork;

public interface WebRequestFilter {

	AsyncWork<FilterResult, Exception> filter(WebRequest request);
	
	public enum FilterResult {
		/** Indicates that any processing should be stopped (except post-filtering) and the response should be send. */
		STOP_PROCESSING,
		/** Indicates the request has been modified and must be re-processed from the beginning.
		 * The path should have change to avoid infinite loop.
		 */
		RESTART_PROCESSING,
		/** Indicates the processing can continue normally. */
		CONTINUE_PROCESSING
	}
	
}
