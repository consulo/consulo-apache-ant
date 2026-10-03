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

import consulo.apache.ant.impl.localize.ApacheAntImplLocalize;
import consulo.component.util.config.AbstractProperty;
import consulo.component.util.config.Externalizer;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.logging.Logger;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.Comparator;
import java.util.Objects;

public abstract class AntReference {
    private static final Logger LOG = Logger.getInstance(AntReference.class);
    private static final String PROJECT_DEFAULT_ATTR = "projectDefault";
    private static final String NAME_ATTR = "name";
    private static final String BUNDLED_ANT_ATTR = "bundledAnt";

    public static final Externalizer<AntReference> EXTERNALIZER = new Externalizer<>() {
        @Override
        public AntReference readValue(Element dataElement) {
            if (Boolean.valueOf(dataElement.getAttributeValue(PROJECT_DEFAULT_ATTR))) {
                return PROJECT_DEFAULT;
            }
            if (Boolean.valueOf(dataElement.getAttributeValue(BUNDLED_ANT_ATTR))) {
                return BUNDLED_ANT;
            }
            String name = dataElement.getAttributeValue(NAME_ATTR);
            if (name == null) {
                throw new IllegalStateException();
            }
            return new MissingAntReference(name);
        }

        @Override
        public void writeValue(Element dataElement, AntReference antReference) {
            antReference.writeExternal(dataElement);
        }
    };
    public static final Comparator<AntReference> COMPARATOR = new Comparator<>() {
        @Override
        public int compare(AntReference reference, AntReference reference1) {
            if (reference.equals(reference1)) {
                return 0;
            }
            if (reference == BUNDLED_ANT) {
                return -1;
            }
            if (reference1 == BUNDLED_ANT) {
                return 1;
            }
            return reference.getName().compareToIgnoreCase(reference1.getName());
        }
    };

    protected abstract void writeExternal(Element dataElement);

    @Override
    public String toString() {
        return getName();
    }

    public static final AntReference PROJECT_DEFAULT = new AntReference() {
        @Override
        protected void writeExternal(Element dataElement) {
            dataElement.setAttribute(PROJECT_DEFAULT_ATTR, Boolean.TRUE.toString());
        }

        @Override
        public Sdk find(GlobalAntConfiguration ants) {
            throw new UnsupportedOperationException("Should not call");
        }

        @Override
        public AntReference bind(GlobalAntConfiguration antConfiguration) {
            return this;
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("Should not call");
        }

        @Override
        @SuppressWarnings({"HardCodedStringLiteral"})
        public String toString() {
            return "PROJECT_DEFAULT";
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }
    };

    public static final AntReference BUNDLED_ANT = new AntReference() {
        @Override
        protected void writeExternal(Element dataElement) {
            dataElement.setAttribute(BUNDLED_ANT_ATTR, Boolean.TRUE.toString());
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public String getName() {
            return GlobalAntConfiguration.BUNDLED_ANT_NAME;
        }

        @Override
        public Sdk find(GlobalAntConfiguration antConfiguration) {
            return antConfiguration.findBundleAntBundle();
        }

        @Override
        public AntReference bind(GlobalAntConfiguration antConfiguration) {
            return this;
        }
    };

    public abstract String getName();

    public abstract Sdk find(GlobalAntConfiguration antConfiguration);

    public abstract AntReference bind(GlobalAntConfiguration antConfiguration);

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == PROJECT_DEFAULT) {
            return this == PROJECT_DEFAULT;
        }
        if (obj == BUNDLED_ANT) {
            return this == BUNDLED_ANT;
        }
        return obj instanceof AntReference that
            && Objects.equals(getName(), that.getName());
    }

    @Nullable
    public static Sdk findAnt(AbstractProperty<AntReference> property, AbstractProperty.AbstractPropertyContainer container) {
        GlobalAntConfiguration antConfiguration = GlobalAntConfiguration.INSTANCE.get(container);
        LOG.assertTrue(antConfiguration != null);
        AntReference antReference = property.get(container);
        if (antReference == PROJECT_DEFAULT) {
            antReference = AntConfigurationImpl.DEFAULT_ANT.get(container);
        }
        if (antReference == null) {
            return null;
        }
        return antReference.find(antConfiguration);
    }

    public static Sdk findNotNullAnt(
        AbstractProperty<AntReference> property,
        AbstractProperty.AbstractPropertyContainer container,
        GlobalAntConfiguration antConfiguration
    ) throws CantRunException {
        AntReference antReference = property.get(container);
        if (antReference == PROJECT_DEFAULT) {
            antReference = AntConfigurationImpl.DEFAULT_ANT.get(container);
        }
        if (antReference == null) {
            throw new CantRunException(ApacheAntImplLocalize.cantRunAntNoAntConfiguredErrorMessage());
        }
        Sdk antInstallation = antReference.find(antConfiguration);
        if (antInstallation == null) {
            throw new CantRunException(ApacheAntImplLocalize.cantRunAntAntReferenceIsNotConfiguredErrorMessage(antReference.getName()));
        }
        return antInstallation;
    }

    @Nullable
    public static Sdk findAntOrBundled(AbstractProperty.AbstractPropertyContainer container) {
        GlobalAntConfiguration antConfiguration = GlobalAntConfiguration.INSTANCE.get(container);
        if (container.hasProperty(AntBuildFileImpl.ANT_REFERENCE)) {
            return findAnt(AntBuildFileImpl.ANT_REFERENCE, container);
        }
        return antConfiguration.findBundleAntBundle();
    }

    static class MissingAntReference extends AntReference {
        private final String myName;

        public MissingAntReference(String name) {
            myName = name;
        }

        @Override
        protected void writeExternal(Element dataElement) {
            dataElement.setAttribute(NAME_ATTR, myName);
        }

        @Override
        public String getName() {
            return myName;
        }

        @Override
        public Sdk find(GlobalAntConfiguration antConfiguration) {
            return antConfiguration.getConfiguredAnts().get(this);
        }

        @Override
        public AntReference bind(GlobalAntConfiguration antConfiguration) {
            Sdk antInstallation = find(antConfiguration);
            if (antInstallation != null) {
                return new BindedReference(antInstallation);
            }
            return this;
        }
    }

    public static class BindedReference extends AntReference {
        private final Sdk myAnt;

        public BindedReference(Sdk ant) {
            myAnt = ant;
        }

        @Override
        public Sdk find(GlobalAntConfiguration antConfiguration) {
            return myAnt;
        }

        @Override
        public String getName() {
            return myAnt.getName();
        }

        @Override
        protected void writeExternal(Element dataElement) {
            dataElement.setAttribute(NAME_ATTR, getName());
        }

        @Override
        public AntReference bind(GlobalAntConfiguration antConfiguration) {
            return this;
        }
    }
}
