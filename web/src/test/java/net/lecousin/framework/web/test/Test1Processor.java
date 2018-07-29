package net.lecousin.framework.web.test;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.buffering.ByteArrayIO;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestProcessor;

public class Test1Processor implements WebRequestProcessor {

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
		return Boolean.TRUE;
	}
	
	@SuppressWarnings("resource")
	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		request.getResponse().setStatus(200);
		request.getResponse().getMIME().setBodyToSend(new ByteArrayIO("This is test 1".getBytes(), "test"));
		return new SynchronizationPoint<>(true);
	}
	
}
