package net.lecousin.framework.web.servlet3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.network.TCPRemote;
import net.lecousin.framework.util.Provider;

public class RemoteBridge implements TCPRemote {

	public RemoteBridge(HttpServletRequest request, AsyncContext ctx) {
		this.request = request;
		this.ctx = ctx;
	}
	
	private HttpServletRequest request;
	private AsyncContext ctx;
	
	@Override
	public SocketAddress getLocalAddress() {
		return new InetSocketAddress(request.getLocalAddr(), request.getLocalPort());
	}

	@Override
	public SocketAddress getRemoteAddress() {
		return new InetSocketAddress(request.getRemoteAddr(), request.getRemotePort());
	}

	@Override
	public ISynchronizationPoint<IOException> send(ByteBuffer data) {
		// TODO
		return new SynchronizationPoint<>(new IOException("Send not yet implemented"));
	}

	@Override
	public void newDataToSendWhenPossible(Provider<ByteBuffer> dataProvider, SynchronizationPoint<IOException> sp) {
		// TODO
		sp.error(new IOException("Send not yet implemented"));
	}
	
	@Override
	public void onclosed(Runnable listener) {
		ctx.addListener(new AsyncListener() {
			@Override
			public void onTimeout(AsyncEvent event) {
				listener.run();
			}
			
			@Override
			public void onStartAsync(AsyncEvent event) {
			}
			
			@Override
			public void onError(AsyncEvent event) {
				listener.run();
			}
			
			@Override
			public void onComplete(AsyncEvent event) {
				listener.run();
			}
		});
	}

}
