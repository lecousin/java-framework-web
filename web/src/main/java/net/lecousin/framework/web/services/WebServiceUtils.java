package net.lecousin.framework.web.services;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.serialization.Deserializer;
import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.network.http.HTTPRequest;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.web.WebResourcesBundle;

/** Utility methods for web services. */
public final class WebServiceUtils {
	
	private WebServiceUtils() { /* no instance */ }
	
	/** Instantiate an object for the given type, by parsing the query parameter given in <code>name</code>. */
	public static Object fillFromParameter(String name, Class<?> type, HTTPRequest request) throws Exception {
		String value = request.getParameter(name);
		if (value == null)
			return null;
		value = URLDecoder.decode(value, StandardCharsets.US_ASCII.name());
		
		if (Boolean.class.equals(type) || boolean.class.equals(type)) {
			String s = value.toLowerCase();
			if (s.equals("true") || s.equals("yes") || s.equals("on") || s.equals("1"))
				return Boolean.TRUE;
			if (s.equals("false") || s.equals("no") || s.equals("off") || s.equals("0"))
				return Boolean.FALSE;
			throw new Exception("Invalid boolean value for parameter " + name + ": " + value);
		}
		
		if (byte.class.equals(type) || Byte.class.equals(type)) {
			try { return new Byte(Byte.parseByte(value)); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (short.class.equals(type) || Short.class.equals(type)) {
			try { return new Short(Short.parseShort(value)); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (int.class.equals(type) || Integer.class.equals(type)) {
			try { return new Integer(Integer.parseInt(value)); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (long.class.equals(type) || Long.class.equals(type)) {
			try { return new Long(Long.parseLong(value)); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (BigInteger.class.equals(type)) {
			try { return new BigInteger(value); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (float.class.equals(type) || Float.class.equals(type)) {
			try { return new Float(Float.parseFloat(value)); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (double.class.equals(type) || Double.class.equals(type)) {
			try { return new Double(Double.parseDouble(value)); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (BigDecimal.class.equals(type)) {
			try { return new BigDecimal(value); }
			catch (NumberFormatException ex) { throw new Exception("Invalid value for parameter " + name + ": " + value); }
		}
		if (String.class.equals(type)) {
			return value;
		}
		if (Character.class.equals(type) || char.class.equals(type)) {
			if (value.length() == 1)
				return new Character(value.charAt(0));
			throw new Exception("Invalid value for parameter " + name + ": " + value);
		}
		throw new Exception("Parameter type " + type.getName() + " is not supported");
	}
	
	/** Deserialize the given type from the body of the request. */
	@SuppressWarnings("resource")
	public static AsyncWork<?, Exception> fillFromBody(
		Class<?> type, ParameterizedType ptype, HTTPRequest request, WebResourcesBundle bundle
	) throws Exception {
		IO.Readable.Seekable body = (IO.Readable.Seekable)request.getMIME().getBodyOutputAsInput();
		if (body == null)
			throw new Exception("No data received");
		try {
			Pair<String, Map<String, String>> ct = request.getMIME().parseContentType();
			if (ct == null)
				throw new Exception("No Content-Type specified");
			
			String mimeType = ct.getValue1();
			String encoding = ct.getValue2().get("charset");
			Charset charset = null;
			if (encoding != null) charset = Charset.forName(encoding);

			Deserializer d = bundle.getDeserializer(mimeType, charset, type);
			if (d == null)
				throw new Exception("Content-Type not supported: " + mimeType);
			
			TypeDefinition t;
			if (ptype == null)
				t = new TypeDefinition(type);
			else {
				Type[] pt = ptype.getActualTypeArguments();
				TypeDefinition[] p = new TypeDefinition[pt.length];
				for (int i = 0; i < pt.length; ++i)
					p[i] = new TypeDefinition(null, pt[i]);
				t = new TypeDefinition(type, p);
			}
			AsyncWork<Object, Exception> result = d.deserialize(t, body, bundle.getSerializationRules());
			result.listenInline(() -> { body.closeAsync(); });
			return result;
		} catch (Exception e) {
			body.closeAsync();
			throw e;
		}
	}
	
}
