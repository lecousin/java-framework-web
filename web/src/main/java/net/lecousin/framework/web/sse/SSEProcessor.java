package net.lecousin.framework.web.sse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.buffering.ByteBuffersIO;
import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.json.JSONSerializer;
import net.lecousin.framework.network.TCPRemote;
import net.lecousin.framework.network.http.HTTPResponse;
import net.lecousin.framework.network.mime.MimeHeader;
import net.lecousin.framework.network.mime.MimeMessage;
import net.lecousin.framework.network.mime.header.ParameterizedHeaderValues;
import net.lecousin.framework.web.WebRequest;
import net.lecousin.framework.web.WebRequestProcessor;

/**
 * Server-side event processor, which sends every message to all clients.
 * It means it must be used only when all messages are required to be received by the clients.
 * It means also it should not be used if a lot of messages can be sent in a short period, as
 * messages will be waiting in memory for slow clients.
 */
public class SSEProcessor implements WebRequestProcessor {
	
	protected WebRequestProcessor parent;
	protected ArrayList<TCPRemote> clients = new ArrayList<>();
	
	@Override
	public WebRequestProcessor getParent() {
		return parent;
	}
	
	@Override
	public void setParent(WebRequestProcessor parent) {
		this.parent = parent;
	}
	
	public ISynchronizationPoint<?> sendMessage(String id, String name, String data) {
		return sendMessage(id, name, data.getBytes(StandardCharsets.UTF_8));
	}

	public ISynchronizationPoint<?> sendMessage(String id, String name, byte[] data) {
		return sendMessage(
			id == null ? null : id.getBytes(StandardCharsets.UTF_8),
			name == null ? null : name.getBytes(StandardCharsets.UTF_8),
			data
		);
	}

	public ISynchronizationPoint<?> sendMessageToClient(String id, String name, String data, TCPRemote client) {
		return sendMessageToClient(id, name, data.getBytes(StandardCharsets.UTF_8),client);
	}

	public ISynchronizationPoint<?> sendMessageToClient(String id, String name, byte[] data, TCPRemote client) {
		return sendMessageToClient(
			id == null ? null : id.getBytes(StandardCharsets.UTF_8),
			name == null ? null : name.getBytes(StandardCharsets.UTF_8),
			data,
			client
		);
	}
	
	@SuppressWarnings("resource")
	public ISynchronizationPoint<?> sendJSONMessage(String id, String name, Object data) {
		JSONSerializer ser = new JSONSerializer(StandardCharsets.UTF_8, 1024, false);
		ByteBuffersIO output = new ByteBuffersIO(false, "JSON message", Task.PRIORITY_NORMAL);
		ISynchronizationPoint<Exception> synch = ser.serialize(data, new TypeDefinition(data == null ? Object.class : data.getClass()), output, new ArrayList<>(0));
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		synch.listenAsync(new Task.Cpu<Void,NoException>("Send JSON Message to SSE clients", Task.PRIORITY_NORMAL) {
			@Override
			public Void run() {
				sendMessage(id, name, output.createSingleByteArray()).listenInlineSP(sp);
				return null;
			}
		}, true);
		return sp;
	}
	
	private static final byte NEW_LINE = '\n';
	private static final byte[] _id = { 'i','d',':',' ' };
	private static final byte[] _event = { 'e','v','e','n','t',':',' ' };
	private static final byte[] _data = { 'd','a','t','a',':',' ' };

	public ISynchronizationPoint<?> sendMessage(byte[] id, byte[] name, byte[] data) {
		if (clients.isEmpty()) return new SynchronizationPoint<>(true);
		byte[] msg = buildMessage(id, name, data);
		JoinPoint<IOException> jp = new JoinPoint<>();
		synchronized (clients) {
			for (int i = clients.size()-1; i >= 0; --i) {
				try { jp.addToJoin(clients.get(i).send(ByteBuffer.wrap(msg))); }
				catch (Throwable t) {
					removeClient(clients.get(i));
				}
			}
		}
		jp.start();
		return jp;
	}
	
	public ISynchronizationPoint<?> sendMessageToClient(byte[] id, byte[] name, byte[]data, TCPRemote client) {
		byte[] msg = buildMessage(id, name, data);
		try { return client.send(ByteBuffer.wrap(msg)); }
		catch (Throwable t) {
			removeClient(client);
			return new SynchronizationPoint<>(true);
		}
	}
	
	protected byte[] buildMessage(byte[] id, byte[] name, byte[] data) {
		int len = 0;
		if (id != null)
			len += 5 /*id: \n */ + id.length;
		if (name != null)
			len += 8 /*event: \n */ + name.length;
		if (data != null) {
			// TODO in \r or \n or \r\n in data, make multiple lines with data: xxx\n
			len += 7 /*data: \n */ + data.length;
		}
		len++; /* \n */
		byte[] msg = new byte[len];
		int pos = 0;
		if (id != null) {
			System.arraycopy(_id, 0, msg, pos, _id.length);
			pos += _id.length;
			System.arraycopy(id, 0, msg, pos, id.length);
			pos += id.length;
			msg[pos++] = NEW_LINE;
		}
		if (name != null) {
			System.arraycopy(_event, 0, msg, pos, _event.length);
			pos += _event.length;
			System.arraycopy(name, 0, msg, pos, name.length);
			pos += name.length;
			msg[pos++] = NEW_LINE;
		}
		if (data != null) {
			System.arraycopy(_data, 0, msg, pos, _data.length);
			pos += _data.length;
			System.arraycopy(data, 0, msg, pos, data.length);
			pos += data.length;
			msg[pos++] = NEW_LINE;
		}
		msg[pos] = NEW_LINE;
		return msg;
	}
	
	@Override
	public Object checkProcessing(WebRequest request) {
		boolean acceptEventStream = false;
		for (MimeHeader h : request.getRequest().getMIME().getHeaders("Accept")) {
			try {
				ParameterizedHeaderValues list = h.getValue(ParameterizedHeaderValues.class);
				if (list.hasMainValue("text/event-stream")) {
					acceptEventStream = true;
					break;
				}
			} catch (Throwable t) {
				// ignore
			}
		}
		if (!acceptEventStream)
			return null;
		if (request.getSubPath().length() > 0)
			return null;
		return Boolean.TRUE;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		HTTPResponse response = request.getResponse();
		response.noCache();
		response.setRawContentType("text/event-stream");
		response.getMIME().setHeaderRaw(MimeMessage.CONNECTION, "keep-alive");
		try {
			return newClient(request);
		} catch (Throwable t) {
			removeClient(request.getClient());
			return new SynchronizationPoint<>(true);
		}
	}
	
	protected ISynchronizationPoint<?> newClient(WebRequest request) {
		synchronized (clients) { clients.add(request.getClient()); }
		request.getClient().onclosed(new Runnable() {
			@Override
			public void run() {
				removeClient(request.getClient());
			}
		});
		return new SynchronizationPoint<>(true);
	}
	
	protected void removeClient(TCPRemote client) {
		synchronized (clients) { clients.remove(client); }
	}
	
}
