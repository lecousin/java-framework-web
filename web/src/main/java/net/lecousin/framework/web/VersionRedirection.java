package net.lecousin.framework.web;

import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.serialization.annotations.Transient;

/**
 * Processor to automatically redirect to newest version.
 * The processor must be placed like in the following example:
 * <pre>{@code
 * <bundle path="version_redirection">
 *	<bundle path="1.6.7">
 *	...
 *	</bundle>
 *	<processor path="" class="net.lecousin.framework.web.VersionRedirection">
 *		<attribute name="version" value="1.6.7"/>
 *	</processor>
 * </bundle>
 * }</pre>
 */
public class VersionRedirection implements WebRequestProcessor {

	private String version = null;
	@Transient
	private WebRequestProcessor parent = null;
	
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
		if (version == null)
			return null;
		String s = request.getSubPath();
		int i = s.indexOf('/');
		if (i < 0)
			return Boolean.TRUE;
		String v = s.substring(0, i);
		if (v.equals(version))
			return null;
		return Boolean.TRUE;
	}
	
	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		String s = request.getSubPath();
		int i = s.indexOf('/');
		if (i < 0) {
			if (s.length() == 0) {
				StringBuilder loc = new StringBuilder(request.getFullPath());
				if (loc.charAt(loc.length() - 1) != '/') loc.append('/');
				loc.append(version).append('/');
				request.getResponse().redirectPerm(loc.toString());
			} else
				request.getResponse().redirectPerm(request.getCurrentPath() + version + "/" + s);
			return new SynchronizationPoint<>(true);
		}
		request.getResponse().redirectPerm(request.getCurrentPath() + version + s.substring(i));
		return new SynchronizationPoint<>(true);
	}
	
}
