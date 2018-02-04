package net.lecousin.framework.web;

import java.io.File;
import java.util.List;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.serialization.annotations.Transient;
import net.lecousin.framework.network.mime.MimeType;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.PathPattern;

public class StaticResourcesProcessor implements WebRequestProcessor {

	public StaticResourcesProcessor() {
	}
	
	public StaticResourcesProcessor(String resourcePath) {
		this.fromClassPath = resourcePath;
	}
	
	public StaticResourcesProcessor(File dir) {
		this.fromFileSystem = dir.getAbsolutePath();
	}
	
	@Transient
	private WebRequestProcessor parent;
	
	private String fromClassPath = null;
	private String fromFileSystem = null;
	private List<String> directoryPages = null;
	private List<String> restrictPatterns = null;
	
	@Override
	public WebRequestProcessor getParent() {
		return parent;
	}
	
	@Override
	public void setParent(WebRequestProcessor parent) {
		this.parent = parent;
	}
	
	public String getFromClassPath() {
		return fromClassPath;
	}

	public void setFromClassPath(String fromClassPath) {
		this.fromClassPath = fromClassPath;
	}

	public String getFromFileSystem() {
		return fromFileSystem;
	}

	public void setFromFileSystem(String fromFileSystem) {
		this.fromFileSystem = fromFileSystem;
	}

	public List<String> getDirectoryPages() {
		return directoryPages;
	}

	public void setDirectoryPages(List<String> directoryPages) {
		this.directoryPages = directoryPages;
	}

	public List<String> getRestrictPatterns() {
		return restrictPatterns;
	}

	public void setRestrictPatterns(List<String> restrictPatterns) {
		this.restrictPatterns = restrictPatterns;
	}

	@SuppressWarnings("resource")
	@Override
	public Object checkProcessing(WebRequest request) {
		String filename = request.getSubPath();
		if (restrictPatterns != null && !restrictPatterns.isEmpty()) {
			boolean ok = false;
			for (String rp : restrictPatterns)
				if (new PathPattern(rp).matches(filename)) {
					ok = true;
					break;
				}
			if (!ok) return null;
		}
		IO.Readable file = null;
		if (fromClassPath != null) {
			if (!fromClassPath.endsWith("/"))
				fromClassPath += "/";
			if (!filename.endsWith("/") && !filename.isEmpty())
				file = LCCore.getApplication().getResource(fromClassPath + filename, Task.PRIORITY_NORMAL);
			if (file == null && directoryPages != null) {
				for (String page : directoryPages) {
					String path = filename;
					if (!path.endsWith("/"))
						path += "/";
					path += page;
					file = LCCore.getApplication().getResource(fromClassPath + path, Task.PRIORITY_NORMAL);
					if (file != null) {
						filename = path;
						break;
					}
				}
			}
		}
		if (file == null && fromFileSystem != null) {
			if (!fromFileSystem.endsWith("/") && !fromFileSystem.endsWith("\\"))
				fromFileSystem += "/";
			File f = new File(fromFileSystem + filename);
			if (f.exists() && !f.isDirectory())
				file = new FileIO.ReadOnly(f, Task.PRIORITY_NORMAL);
			if (file == null && directoryPages != null) {
				for (String page : directoryPages) {
					File fp = new File(f, page);
					if (fp.exists()) {
						file = new FileIO.ReadOnly(fp, Task.PRIORITY_NORMAL);
						filename = fp.getName();
						break;
					}
				}
			}
		}
		if (file == null)
			return null;
		int i = filename.lastIndexOf('/');
		if (i >= 0) filename = filename.substring(i + 1);
		i = filename.lastIndexOf('\\');
		if (i >= 0) filename = filename.substring(i + 1);
		// TODO close file if something happen
		return new Pair<>(filename, file);
	}
	
	@Override
	public ISynchronizationPoint<? extends Exception> process(Object fromCheck, WebRequest request) {
		@SuppressWarnings("unchecked")
		Pair<String, IO.Readable> p = (Pair<String, IO.Readable>)fromCheck;
		String ext = p.getValue1();
		int i = ext.lastIndexOf('.');
		if (i < 0) ext = "";
		else ext = ext.substring(i + 1).toLowerCase();
		String type = MimeType.defaultByExtension.get(ext);
		if (type == null)
			type = "application/octet-stream";
		request.getResponse().setStatus(200);
		request.getResponse().setRawContentType(type);
		request.getResponse().getMIME().setBodyToSend(p.getValue2());
		return new SynchronizationPoint<>(true);
	}
	
}
