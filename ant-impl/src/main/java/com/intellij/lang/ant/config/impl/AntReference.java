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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import consulo.component.util.config.AbstractProperty;
import consulo.component.util.config.Externalizer;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.logging.Logger;
import consulo.util.lang.Comparing;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import java.util.Comparator;

public abstract class AntReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntReference");
  @NonNls private static final String PROJECT_DEFAULT_ATTR = "projectDefault";
  @NonNls private static final String NAME_ATTR = "name";
  @NonNls private static final String BUNDLED_ANT_ATTR = "bundledAnt";

  public static final Externalizer<AntReference> EXTERNALIZER = new Externalizer<AntReference>() {
    public AntReference readValue(Element dataElement)  {
      if (Boolean.valueOf(dataElement.getAttributeValue(PROJECT_DEFAULT_ATTR)).booleanValue()) return PROJECT_DEFAULT;
      if (Boolean.valueOf(dataElement.getAttributeValue(BUNDLED_ANT_ATTR)).booleanValue()) return BUNDLED_ANT;
      String name = dataElement.getAttributeValue(NAME_ATTR);
      if (name == null) throw new IllegalStateException();
      return new MissingAntReference(name);
    }

    public void writeValue(Element dataElement, AntReference antReference) {
      antReference.writeExternal(dataElement);
    }
  };
  public static final Comparator<AntReference> COMPARATOR = new Comparator<AntReference>() {
    public int compare(AntReference reference, AntReference reference1) {
      if (reference.equals(reference1)) return 0;
      if (reference == BUNDLED_ANT) return -1;
      if (reference1 == BUNDLED_ANT) return 1;
      return reference.getName().compareToIgnoreCase(reference1.getName());
    }
  };

  protected abstract void writeExternal(Element dataElement);

  public String toString() {
    return getName();
  }

  public static final AntReference PROJECT_DEFAULT = new AntReference() {
    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(PROJECT_DEFAULT_ATTR, Boolean.TRUE.toString());
    }

    public Sdk find(GlobalAntConfiguration ants) {
      throw new UnsupportedOperationException("Should not call");
    }

    public AntReference bind(GlobalAntConfiguration antConfiguration) {
      return this;
    }

    public String getName() {
      throw new UnsupportedOperationException("Should not call");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "PROJECT_DEFAULT";
    }

    public boolean equals(Object obj) {
      return obj == this;
    }
  };

  public static final AntReference BUNDLED_ANT = new AntReference() {
    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(BUNDLED_ANT_ATTR, Boolean.TRUE.toString());
    }

    public boolean equals(Object obj) {
      return obj == this;
    }

    public String getName() {
      return GlobalAntConfiguration.BUNDLED_ANT_NAME;
    }

    public Sdk find(GlobalAntConfiguration antConfiguration) {
      return antConfiguration.findBundleAntBundle();
    }

    public AntReference bind(GlobalAntConfiguration antConfiguration) {
      return this;
    }
  };

  public abstract String getName();

  public abstract Sdk find(GlobalAntConfiguration antConfiguration);

  public abstract AntReference bind(GlobalAntConfiguration antConfiguration);

  public int hashCode() {
    return getName().hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == PROJECT_DEFAULT) return this == PROJECT_DEFAULT;
    if (obj == BUNDLED_ANT) return this == BUNDLED_ANT;
    return obj instanceof AntReference && Comparing.equal(getName(), ((AntReference)obj).getName());
  }

  @Nullable
  public static Sdk findAnt(AbstractProperty<AntReference> property, AbstractProperty.AbstractPropertyContainer container) {
    GlobalAntConfiguration antConfiguration = GlobalAntConfiguration.INSTANCE.get(container);
    LOG.assertTrue(antConfiguration != null);
    AntReference antReference = property.get(container);
    if (antReference == PROJECT_DEFAULT) {
      antReference = AntConfigurationImpl.DEFAULT_ANT.get(container);
    }
    if (antReference == null) return null;
    return antReference.find(antConfiguration);
  }

  public static Sdk findNotNullAnt(AbstractProperty<AntReference> property,
														  AbstractProperty.AbstractPropertyContainer container,
														  GlobalAntConfiguration antConfiguration) throws CantRunException {
    AntReference antReference = property.get(container);
    if (antReference == PROJECT_DEFAULT) antReference = AntConfigurationImpl.DEFAULT_ANT.get(container);
    if (antReference == null) throw new CantRunException(AntBundle.message("cant.run.ant.no.ant.configured.error.message"));
	  Sdk antInstallation = antReference.find(antConfiguration);
    if (antInstallation == null) {
      throw new CantRunException(AntBundle.message("cant.run.ant.ant.reference.is.not.configured.error.message", antReference.getName()));
    }
    return antInstallation;
  }

  @Nullable
  public static Sdk findAntOrBundled(AbstractProperty.AbstractPropertyContainer container) {
    GlobalAntConfiguration antConfiguration = GlobalAntConfiguration.INSTANCE.get(container);
    if (container.hasProperty(AntBuildFileImpl.ANT_REFERENCE)) return findAnt(AntBuildFileImpl.ANT_REFERENCE, container);
    return antConfiguration.findBundleAntBundle();
  }

  static class MissingAntReference extends AntReference {
    private final String myName;

    public MissingAntReference(String name) {
      myName = name;
    }

    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(NAME_ATTR, myName);
    }

    public String getName() {
      return myName;
    }

    public Sdk find(GlobalAntConfiguration antConfiguration) {
      return antConfiguration.getConfiguredAnts().get(this);
    }

    public AntReference bind(GlobalAntConfiguration antConfiguration) {
		Sdk antInstallation = find(antConfiguration);
      if (antInstallation != null) return new BindedReference(antInstallation);
      return this;
    }
  }

  public static class BindedReference extends AntReference {
    private final Sdk myAnt;

    public BindedReference(Sdk ant) {
      myAnt = ant;
    }

    public Sdk find(GlobalAntConfiguration antConfiguration) {
      return myAnt;
    }

    public String getName() {
      return myAnt.getName();
    }

    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(NAME_ATTR, getName());
    }

    public AntReference bind(GlobalAntConfiguration antConfiguration) {
      return this;
    }
  }
}
