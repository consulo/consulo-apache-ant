/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import consulo.component.ProcessCanceledException;
import consulo.logging.Logger;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nullable;

import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
* @author Eugene Zhuravlev
*         Date: Apr 9, 2010
*/
public final class ReflectedProject {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.ReflectedProject");
  
  @NonNls private static final String INIT_METHOD_NAME = "init";
  @NonNls private static final String GET_TASK_DEFINITIONS_METHOD_NAME = "getTaskDefinitions";
  @NonNls private static final String GET_DATA_TYPE_DEFINITIONS_METHOD_NAME = "getDataTypeDefinitions";
  @NonNls private static final String GET_PROPERTIES_METHOD_NAME = "getProperties";


  private static final List<SoftReference<Pair<ReflectedProject, ClassLoader>>> ourProjects =
    new ArrayList<SoftReference<Pair<ReflectedProject, ClassLoader>>>();

  private static final ReentrantLock ourProjectsLock = new ReentrantLock();
  
  private final Object myProject;
  private Hashtable myTaskDefinitions;
  private Hashtable myDataTypeDefinitions;
  private Hashtable myProperties;
  private Class myTargetClass;

  public static ReflectedProject getProject(final ClassLoader classLoader) {
    ourProjectsLock.lock();
    try {
      for (Iterator<SoftReference<Pair<ReflectedProject, ClassLoader>>> iterator = ourProjects.iterator(); iterator.hasNext();) {
        final SoftReference<Pair<ReflectedProject, ClassLoader>> ref = iterator.next();
        final Pair<ReflectedProject, ClassLoader> pair = ref.get();
        if (pair == null) {
          iterator.remove();
        }
        else {
          if (pair.second == classLoader) {
            return pair.first;
          }
        }
      }
    }
    finally {
      ourProjectsLock.unlock();
    }
    final ReflectedProject reflectedProj = new ReflectedProject(classLoader);
    ourProjectsLock.lock();
    try {
      ourProjects.add(new SoftReference<Pair<ReflectedProject, ClassLoader>>(
        new Pair<ReflectedProject, ClassLoader>(reflectedProj, classLoader)
      ));
    }
    finally {
      ourProjectsLock.unlock();
    }
    return reflectedProj;
  }

  ReflectedProject(final ClassLoader classLoader) {
    Object project = null;
    try {
      final Class projectClass = classLoader.loadClass("org.apache.tools.ant.Project");
      if (projectClass != null) {
        project = projectClass.newInstance();
        Method method = projectClass.getMethod(INIT_METHOD_NAME);
        method.invoke(project);
        method = getMethod(projectClass, GET_TASK_DEFINITIONS_METHOD_NAME);
        myTaskDefinitions = (Hashtable)method.invoke(project);
        method = getMethod(projectClass, GET_DATA_TYPE_DEFINITIONS_METHOD_NAME);
        myDataTypeDefinitions = (Hashtable)method.invoke(project);
        method = getMethod(projectClass, GET_PROPERTIES_METHOD_NAME);
        myProperties = (Hashtable)method.invoke(project);
        myTargetClass = classLoader.loadClass("org.apache.tools.ant.Target");
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (ExceptionInInitializerError e) {
      final Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      else {
        LOG.info(e);
        project = null;
      }
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      else {
        LOG.info(e);
        project = null;
      }
    }
    catch (Throwable e) {
      LOG.info(e);
      project = null;
    }
    myProject = project;
  }

  private static Method getMethod(final Class introspectionHelperClass, final String name) throws NoSuchMethodException {
    final Method method;
    method = introspectionHelperClass.getMethod(name);
    if (!method.isAccessible()) {
      method.setAccessible(true);
    }
    return method;
  }

  @Nullable
  public Hashtable<String, Class> getTaskDefinitions() {
    return myTaskDefinitions;
  }

  @Nullable
  public Hashtable<String, Class> getDataTypeDefinitions() {
    return myDataTypeDefinitions;
  }

  public Hashtable getProperties() {
    return myProperties;
  }

  public Class getTargetClass() {
    return myTargetClass;
  }

  @Nullable
  public Object getProject() {
    return myProject;
  }
}
