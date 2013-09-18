package com.javax0.djcproxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import com.javax0.jscc.Compiler;

public class ProxyFactory<ClassToBeProxied> {

	private static Map<Class<?>, Map<CallbackFilter, Class<?>>> cache = new WeakHashMap<>();

	private Class<?> getProxyClass(Class<?> originalClass, CallbackFilter filter) {
		Class<?> proxyClass = null;
		Map<CallbackFilter, Class<?>> classCache = cache.get(originalClass);
		if (classCache != null) {
			proxyClass = classCache.get(filter);
		}
		return proxyClass;
	}

	private void put(Class<?> originalClass, CallbackFilter filter,
			Class<?> proxyClass) {
		Map<CallbackFilter, Class<?>> classCache = cache.get(originalClass);
		if (classCache == null) {
			classCache = new WeakHashMap<>();
			cache.put(originalClass, classCache);
		}
		classCache.put(filter, proxyClass);
	}

	private CallbackFilter callbackFilter = new CallbackFilter() {
		@Override
		public boolean accept(Method method) {
			return true;
		}

	};

	public void setCallbackFilter(CallbackFilter callBackFilter) {
		if (callbackFilter == null) {
			throw new IllegalArgumentException(
					"callback filter can not be null");
		}
		this.callbackFilter = callBackFilter;
	}

	private String source;

	/**
	 * Get the source code that was generated by the factory last time. Since a
	 * factory can be used to generate more than one source class there is no
	 * guarantee that the returned source is the source of the last object
	 * returned. This method is to help debugging and learning how the proxy
	 * factory works. To ensure that the source is the one the caller is
	 * interested in create a new ProxyFactory and use it only once to generate
	 * a proxy object.
	 * 
	 * @return
	 */
	public String getGeneratedSource() {
		return source;
	}

	private String generatedClassName;

	/**
	 * Get the name of the class that was generated last time. The same caveats
	 * hold as in the method {@link #getGeneratedSource()}.
	 * 
	 * @return
	 */
	public String getGeneratedClassName() {
		return generatedClassName;
	}

	/**
	 * Create a new proxy object.
	 * 
	 * @param originalObject
	 *            the object to be proxied
	 * @return the new proxy object
	 * @throws Exception
	 */
	public ClassToBeProxied create(ClassToBeProxied originalObject,
			MethodInterceptor interceptor) throws Exception {
		synchronized (cache) {
			Class<?> proxyClass = getProxyClass(originalObject.getClass(),
					callbackFilter);
			if (proxyClass == null) {
				proxyClass = createClass(originalObject.getClass());
				put(originalObject.getClass(), callbackFilter, proxyClass);
			}
			ProxySetter proxy = instantiateProxy(proxyClass);
			proxy.setPROXY$OBJECT(originalObject);
			proxy.setPROXY$INTERCEPTOR(interceptor);
			return cast(proxy);
		}
	}

	/**
	 * Create a new proxy class that is capable proxying an object that is an
	 * instance of the class
	 * 
	 * @param originalObject
	 * @param interceptor
	 * @return
	 * @throws Exception
	 */
	public Class<?> createClass(Class<?> originalClass) throws Exception {
		ProxySourceFactory<ClassToBeProxied> sourceFactory = new ProxySourceFactory<>(
				callbackFilter);
		source = sourceFactory.create(originalClass);
		Compiler compiler = new Compiler();
		compiler.setClassLoader(originalClass.getClassLoader());
		String classFQN = sourceFactory.getGeneratedPackageName() + "."
				+ sourceFactory.getGeneratedClassName();
		Class<?> proxyClass = compiler.compile(source, classFQN);
		return proxyClass;
	}

	@SuppressWarnings("restriction")
	private ProxySetter instantiateProxy(Class<?> proxyClass) throws Exception {
		ProxySetter proxy;
		sun.misc.Unsafe unsafe;
		try {
			Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (sun.misc.Unsafe) f.get(null);
		} catch (Exception e) {
			unsafe = null;
		}
		// unsafe = null;
		if (unsafe == null) {
			Constructor<?> constructor = proxyClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			proxy = (ProxySetter) constructor.newInstance(new Object[0]);
		} else {
			proxy = (ProxySetter) unsafe.allocateInstance(proxyClass);
		}
		return proxy;
	}

	@SuppressWarnings("unchecked")
	private ClassToBeProxied cast(Object proxy) {
		return (ClassToBeProxied) proxy;
	}
}
