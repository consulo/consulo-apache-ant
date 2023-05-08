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
package com.intellij.lang.ant.doc;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomTarget;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.language.Language;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.pom.PomTarget;
import consulo.language.pom.PomTargetPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.io.FileUtil;
import consulo.util.io.URLUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ZipArchiveFileType;
import consulo.xml.lang.xml.XMLLanguage;
import consulo.xml.psi.xml.XmlElement;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomTarget;
import consulo.xml.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@ExtensionImpl
public class AntDomDocumentationProvider implements LanguageDocumentationProvider {

  private static final Logger LOG = Logger.getInstance(AntDomDocumentationProvider.class);

  public String generateDoc(consulo.language.psi.PsiElement element, PsiElement originalElement) {
    final String mainDoc = getMainDocumentation(originalElement);
    final String additionalDoc = getAdditionalDocumentation(originalElement);
    if (mainDoc == null && additionalDoc == null) {
      return null;
    }
    final StringBuilder builder = new StringBuilder();
    if (additionalDoc != null) {
      builder.append(additionalDoc);
    }
    if (mainDoc != null) {
      builder.append(mainDoc);
    }
    return builder.toString();
  }

  @Nullable
  private static String getMainDocumentation(consulo.language.psi.PsiElement elem) {
    final VirtualFile helpFile = getHelpFile(elem);
    if (helpFile != null) {
      return helpFile.loadText().toString();
    }
    return null;
  }

  @Nullable
  private static String getAdditionalDocumentation(consulo.language.psi.PsiElement elem) {
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(elem, XmlTag.class);
    if (xmlTag == null) {
      return null;
    }
    final AntDomElement antElement = AntSupport.getAntDomElement(xmlTag);
    if (antElement instanceof AntFilesProvider) {
      final List<File> list = ((AntFilesProvider)antElement).getFiles(new HashSet<AntFilesProvider>());
      if (list.size() > 0) {
        final @NonNls StringBuilder builder = new StringBuilder();
        final XmlTag tag = antElement.getXmlTag();
        if (tag != null) {
          builder.append("<b>");
          builder.append(tag.getName());
          builder.append(":</b>");
        }
        for (File file : list) {
          if (builder.length() > 0) {
            builder.append("<br>");
          }
          builder.append(file.getPath());
        }
        return builder.toString();
      }
    }
    return null;
  }

  @Nullable
  private static consulo.virtualFileSystem.VirtualFile getHelpFile(final consulo.language.psi.PsiElement element) {
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (xmlTag == null) {
      return null;
    }
    final AntDomElement antElement = AntSupport.getAntDomElement(xmlTag);
    if (antElement == null) {
      return null;
    }
    final AntDomProject antProject = antElement.getAntProject();
    if (antProject == null) {
      return null;
    }
    final Sdk installation = antProject.getAntInstallation();
    if (installation == null) {
      return null; // not configured properly and bundled installation missing
    }
    final String antHomeDir = installation.getHomePath();

    if (antHomeDir == null) {
      return null;
    }

    @NonNls String path = antHomeDir + "/docs/manual";
    String url;
    if (new File(path).exists()) {
      url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(path));
    }
    else {
      path = antHomeDir + "/docs.zip";
      if (new File(path).exists()) {
        url = consulo.virtualFileSystem.VirtualFileManager.constructUrl(ZipArchiveFileType.PROTOCOL,
                                                                        FileUtil.toSystemIndependentName(path) + URLUtil.ARCHIVE_SEPARATOR + "docs/manual");
      }
      else {
        return null;
      }
    }

    final VirtualFile documentationRoot = VirtualFileManager.getInstance().findFileByUrl(url);
    if (documentationRoot == null) {
      return null;
    }

    return getHelpFile(antElement, documentationRoot);
  }

  public static final String[] DOC_FOLDER_NAMES = new String[]{
    "Tasks", "Types", "CoreTasks", "OptionalTasks", "CoreTypes", "OptionalTypes"
  };

  @Nullable
  private static consulo.virtualFileSystem.VirtualFile getHelpFile(AntDomElement antElement,
                                                                   final consulo.virtualFileSystem.VirtualFile documentationRoot) {
    final XmlTag xmlTag = antElement.getXmlTag();
    if (xmlTag == null) {
      return null;
    }
    @NonNls final String helpFileShortName = "/" + xmlTag.getName() + ".html";

    for (String folderName : DOC_FOLDER_NAMES) {
      final consulo.virtualFileSystem.VirtualFile candidateHelpFile =
        documentationRoot.findFileByRelativePath(folderName + helpFileShortName);
      if (candidateHelpFile != null) {
        return candidateHelpFile;
      }
    }

    if (antElement instanceof AntDomTarget || antElement instanceof AntDomProject) {
      final consulo.virtualFileSystem.VirtualFile candidateHelpFile = documentationRoot.findFileByRelativePath("using.html");
      if (candidateHelpFile != null) {
        return candidateHelpFile;
      }
    }

    return null;
  }

  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {  // todo!
    if (element instanceof PomTargetPsiElement) {
      final PomTarget pomTarget = ((PomTargetPsiElement)element).getTarget();
      if (pomTarget instanceof DomTarget) {
        final DomElement domElement = ((DomTarget)pomTarget).getDomElement();
        if (domElement instanceof AntDomTarget) {
          final AntDomTarget antTarget = (AntDomTarget)domElement;
          final String description = antTarget.getDescription().getRawText();
          if (description != null && description.length() > 0) {
            final String targetName = antTarget.getName().getRawText();
            final StringBuilder builder = new StringBuilder();
            builder.append("Target");
            if (targetName != null) {
              builder.append(" \"").append(targetName).append("\"");
            }
            final XmlElement xmlElement = antTarget.getXmlElement();
            if (xmlElement != null) {
              final PsiFile containingFile = xmlElement.getContainingFile();
              if (containingFile != null) {
                final String fileName = containingFile.getName();
                builder.append(" [").append(fileName).append("]");
              }
            }
            return builder.append(" ").append(description).toString();
          }
        }
      }
      else if (pomTarget instanceof DomChildrenDescription) {
        final DomChildrenDescription description = (DomChildrenDescription)pomTarget;
        Type type = null;
        try {
          type = description.getType();
        }
        catch (UnsupportedOperationException e) {
          LOG.info(e);
        }
        if (type instanceof Class && AntDomElement.class.isAssignableFrom(((Class)type))) {
          final String elemName = description.getName();
          if (elemName != null) {
            final AntDomElement.Role role = description.getUserData(AntDomElement.ROLE);
            final StringBuilder builder = new StringBuilder();
            if (role == AntDomElement.Role.TASK) {
              builder.append("Task ");
            }
            else if (role == AntDomElement.Role.DATA_TYPE) {
              builder.append("Data structure ");
            }
            builder.append(elemName);
            return builder.toString();
          }
        }
      }
    }
    return null;
  }

  public List<String> getUrlFor(PsiElement element, consulo.language.psi.PsiElement originalElement) {
    final consulo.virtualFileSystem.VirtualFile helpFile = getHelpFile(originalElement);
    if (helpFile == null || !(helpFile.getFileSystem() instanceof LocalFileSystem)) {
      return null;
    }
    return Collections.singletonList(helpFile.getUrl());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return XMLLanguage.INSTANCE;
  }
}
