package net.lecousin.framework.web.security;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.web.WebRequest;

public class CompositeAuthenticationProvider implements IAuthenticationProvider {

	public CompositeAuthenticationProvider() {
		this(new LinkedList<>());
	}
	
	public CompositeAuthenticationProvider(List<IAuthenticationProvider> providers) {
		this.providers = providers;
	}
	
	protected List<IAuthenticationProvider> providers;

	@Override
	public ISynchronizationPoint<Exception> authenticate(WebRequest request) {
		if (request.hasAuthentication(this))
			return new SynchronizationPoint<>(true);
		for (IAuthenticationProvider provider : providers)
			if (request.hasAuthentication(provider)) {
				request.addAuthentication(this, request.getAuthentication(provider));
				return new SynchronizationPoint<>(true);
			}
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		auth(providers.iterator(), sp, request);
		return sp;
	}
	
	private void auth(Iterator<IAuthenticationProvider> it, SynchronizationPoint<Exception> sp, WebRequest request) {
		if (!it.hasNext()) {
			sp.unblock();
			return;
		}
		IAuthenticationProvider provider = it.next();
		provider.authenticate(request).listenAsync(new Task.Cpu.FromRunnable("Authentication", Task.PRIORITY_NORMAL, () -> {
			if (request.hasAuthentication(provider)) {
				request.addAuthentication(this, request.getAuthentication(provider));
				sp.unblock();
			} else
				auth(it, sp, request);
		}), true);
	}
	
	@Override
	public void deconnect(IAuthentication auth, WebRequest request) {
		for (IAuthenticationProvider provider : providers)
			request.clearAuthentication(provider);
	}
	
}
