/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nullable;
import consulo.logging.Logger;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.apache.ant.ApacheAntClasses;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 22, 2007
 */
public final class AntIntrospector
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.AntIntrospector");
	private final Object myHelper;
	//private static final ObjectCache<String, SoftReference<Object>> ourCache = new ObjectCache<String, SoftReference<Object>>(300);
	private static final HashMap<Class, Object> ourCache = new HashMap<Class, Object>();
	private static final Object ourNullObject = new Object();

	private static Future<?> ourCacheCleaner = CompletableFuture.completedFuture(null);
	private static final int CACHE_CLEAN_TIMEOUT = 10; // 10 seconds
	private final Class myTypeClass;

	public AntIntrospector(final Class aClass)
	{
		myTypeClass = aClass;
		myHelper = getHelper(aClass);
	}

	@Nullable
	public static AntIntrospector getInstance(Class c)
	{
		final AntIntrospector antIntrospector = new AntIntrospector(c);
		return antIntrospector.myHelper == null ? null : antIntrospector;
	}

	private <T> T invokeMethod(@NonNls String methodName, final boolean ignoreErrors, Object... params)
	{
		final Class helperClass = myHelper.getClass();
		final Class[] types = new Class[params.length];
		try
		{
			for(int idx = 0; idx < params.length; idx++)
			{
				types[idx] = params[idx].getClass();
			}
			final Method method = helperClass.getMethod(methodName, types);
			return (T) method.invoke(myHelper, params);
		}
		catch(IllegalAccessException e)
		{
			if(!ignoreErrors)
			{
				LOG.error(e);
			}
		}
		catch(NoSuchMethodException e)
		{
			if(!ignoreErrors)
			{
				LOG.error(e);
			}
		}
		catch(InvocationTargetException e)
		{
			final Throwable cause = e.getCause();
			if(cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			if(cause instanceof Error)
			{
				throw (Error) cause;
			}
			if(!ignoreErrors)
			{
				LOG.error(e);
			}
		}
		return null;
	}

	public Set<String> getExtensionPointTypes()
	{
		final List<Method> methods = invokeMethod("getExtensionPoints", true);
		if(methods == null || methods.size() == 0)
		{
			return Collections.emptySet();
		}
		final Set<String> types = new HashSet<String>();
		for(Method method : methods)
		{
			final Class<?>[] paramTypes = method.getParameterTypes();
			for(Class<?> paramType : paramTypes)
			{
				types.add(paramType.getName());
			}
		}
		return types;
	}

	public Enumeration<String> getNestedElements()
	{
		return invokeMethod("getNestedElements", false);
	}

	@Nullable
	public Class getElementType(String name)
	{
		try
		{
			return invokeMethod("getElementType", false, name);
		}
		catch(RuntimeException e)
		{
			return null;
		}
	}

	public Enumeration<String> getAttributes()
	{
		return invokeMethod("getAttributes", false);
	}

	@Nullable
	public Class getAttributeType(final String attr)
	{
		try
		{
			return invokeMethod("getAttributeType", false, attr);
		}
		catch(RuntimeException e)
		{
			return null;
		}
	}

	public boolean isContainer()
	{
		try
		{
			final Object isContainer = invokeMethod("isContainer", true);
			if(isContainer != null)
			{
				return Boolean.TRUE.equals(isContainer);
			}
			final ClassLoader loader = myTypeClass.getClassLoader();
			if(loader == null)
			{
				return false;
			}
			try
			{
				final Class<?> containerClass = loader.loadClass(ApacheAntClasses.TaskContainer);
				return containerClass.isAssignableFrom(myTypeClass);
			}
			catch(ClassNotFoundException ignored)
			{
			}
		}
		catch(RuntimeException e)
		{
			LOG.info(e);
		}
		return false;
	}

	// for debug purposes
	//private static int ourAttempts = 0;
	//private static int ourHits = 0;
	@Nullable
	private static Object getHelper(final Class aClass)
	{
		final ClassLoader loader = aClass.getClassLoader();
		//final String key;
		//final StringBuilder builder = StringBuilderSpinAllocator.alloc();
		//try {
		//  builder.append(aClass.getName());
		//  if (loader != null) {
		//    builder.append("_");
		//    builder.append(loader.hashCode());
		//  }
		//  key = builder.toString();
		//}
		//finally {
		//  StringBuilderSpinAllocator.dispose(builder);
		//}

		Object result = null;

		synchronized(ourCache)
		{
			result = ourCache.get(aClass);
			//final SoftReference<Object> ref = ourCache.get(aClass);
			//result = (ref == null) ? null : ref.get();
			//if (result == null && ref != null) {
			//  ourCache.remove(aClass);
			//}
		}

		if(result == null)
		{
			result = ourNullObject;
			Class<?> helperClass = null;
			try
			{
				if(loader == null)
				{
					return null;
				}
				helperClass = loader.loadClass(ApacheAntClasses.IntrospectionHelper);
				final Method getHelperMethod = helperClass.getMethod("getHelper", Class.class);
				result = getHelperMethod.invoke(null, aClass);
			}
			catch(ClassNotFoundException e)
			{
				LOG.info(e);
			}
			catch(NoSuchMethodException e)
			{
				LOG.info(e);
			}
			catch(IllegalAccessException e)
			{
				LOG.info(e);
			}
			catch(InvocationTargetException ignored)
			{
			}

			synchronized(ourCache)
			{
				if(helperClass != null)
				{
					clearAntStaticCache(helperClass);
				}
				//ourCache.put(aClass, new SoftReference<Object>(result));
				ourCache.put(aClass, result);
			}
		}
		scheduleCacheCleaning();
		return result == ourNullObject ? null : result;
	}

	//private static int ourClearAttemptCount = 0;

	private static void clearAntStaticCache(final Class helperClass)
	{
		//if (++ourClearAttemptCount > 1000) { // allow not more than 1000 helpers cached inside ant
		//  ourClearAttemptCount = 0;
		//}
		//else {
		//  return;
		//}

		// for ant 1.7, there is a dedicated method for cache clearing
		try
		{
			final Method method = helperClass.getDeclaredMethod("clearCache");
			method.invoke(null);
		}
		catch(Throwable e)
		{
			try
			{
				// assume it is older version of ant
				final Field helpersField = helperClass.getDeclaredField("helpers");
				final Map helpersCollection = (Map) helpersField.get(null);
				helpersCollection.clear();
			}
			catch(Throwable _e)
			{
				// ignore.
			}
		}
	}

	private static void scheduleCacheCleaning()
	{
		ourCacheCleaner.cancel(false);
		ourCacheCleaner = AppExecutorUtil.getAppScheduledExecutorService().schedule((Runnable) () ->
		{
			synchronized(ourCache)
			{
				ourCache.clear();
			}
		}, CACHE_CLEAN_TIMEOUT, TimeUnit.SECONDS);
	}

}
