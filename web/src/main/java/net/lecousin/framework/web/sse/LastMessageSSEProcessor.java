package net.lecousin.framework.web.sse;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.network.TCPRemote;
import net.lecousin.framework.util.Provider;
import net.lecousin.framework.web.WebRequest;

/**
 * Server-side event processor, which sends only the last message to a client.
 * It means that slow clients may miss messages.
 * This is typically useful when sending a <i>status</i>, because only the last status
 * is important, and missing an intermediate status is not important for clients.
 * <br/>
 * This processor will always keep the last message, so new clients will receive immediately the
 * last status message.
 * Before to send a message to a client, the connection of the client is checked to know if the
 * connection can send data immediately or not. If yes, the new message is sent.
 * If not, we are waiting for the socket to be ready to send new data. Once it is ready, the
 * latest message is send.
 */
public class LastMessageSSEProcessor extends SSEProcessor {

	private byte[] lastMessage = null;
	
	private Provider<ByteBuffer> dataProvider = new Provider<ByteBuffer>() {
		@Override
		public ByteBuffer provide() {
			synchronized (LastMessageSSEProcessor.this) {
				return ByteBuffer.wrap(lastMessage);
			}
		}
	};
	
	@Override
	protected ISynchronizationPoint<?> newClient(WebRequest request) {
		ISynchronizationPoint<?> sendHeaders = super.newClient(request);
		sendHeaders.listenInline(new Runnable() {
			@Override
			public void run() {
				synchronized (LastMessageSSEProcessor.this) {
					if (lastMessage == null)
						return;
					request.getClient().newDataToSendWhenPossible(dataProvider, new SynchronizationPoint<>());
				}
			}
		});
		return sendHeaders;
	}
	
	@Override
	public ISynchronizationPoint<?> sendMessage(byte[] id, byte[] name, byte[] data) {
		JoinPoint<IOException> jp = new JoinPoint<>();
		synchronized (this) {
			lastMessage = buildMessage(id, name, data);
			synchronized (clients) {
				for (TCPRemote client : clients) {
					SynchronizationPoint<IOException> sp = new SynchronizationPoint<>();
					client.newDataToSendWhenPossible(dataProvider, sp);
					jp.addToJoin(sp);
				}
			}
		}
		jp.start();
		return jp;
	}
	
}
