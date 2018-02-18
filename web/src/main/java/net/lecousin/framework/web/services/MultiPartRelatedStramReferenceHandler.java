package net.lecousin.framework.web.services;

import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.serialization.Deserializer;
import net.lecousin.framework.network.mime.MimeMessage;
import net.lecousin.framework.network.mime.entity.MultipartEntity;

public class MultiPartRelatedStramReferenceHandler implements Deserializer.StreamReferenceHandler {

	public MultiPartRelatedStramReferenceHandler(MultipartEntity multipart) {
		this.multipart = multipart;
	}
	
	private MultipartEntity multipart;
	
	@Override
	public boolean isReference(String text) {
		text = text.trim();
		if (!text.toLowerCase().startsWith("cid:")) return false;
		String id = text.substring(4);
		for (MimeMessage part : multipart.getParts()) {
			String cid = part.getFirstHeaderRawValue("Content-ID");
			if (cid == null) continue;
			cid = cid.trim();
			if (id.equals(cid))
				return true;
		}
		return false;
	}

	@Override
	public AsyncWork<Readable, Exception> getStreamFromReference(String text) {
		String id = text.substring(4);
		for (MimeMessage part : multipart.getParts()) {
			String cid = part.getFirstHeaderRawValue("Content-ID");
			if (cid == null) continue;
			cid = cid.trim();
			if (id.equals(cid))
				return new AsyncWork<>(part.getBodyReceivedAsInput(), null);
		}
		return new AsyncWork<>(null, null);
	}

}
