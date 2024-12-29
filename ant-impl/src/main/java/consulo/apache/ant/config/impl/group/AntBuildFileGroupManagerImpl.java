/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.apache.ant.config.impl.group;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntConfigurationBase;
import consulo.annotation.component.ServiceImpl;
import consulo.apache.ant.config.AntBuildFileGroup;
import consulo.apache.ant.config.AntBuildFileGroupManager;
import consulo.component.persist.*;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.xml.psi.xml.XmlFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author VISTALL
 * @since 12:27/09.03.13
 */
@State(name = "AntBuildFileGroupManager", storages = @Storage("ant.xml"))
@ServiceImpl
@Singleton
public class AntBuildFileGroupManagerImpl extends AntBuildFileGroupManager implements PersistentStateComponent<Element> {
  private final Map<AntBuildFileGroup, List<VirtualFile>> myFileGroupList = new HashMap<AntBuildFileGroup, List<VirtualFile>>();
  private final List<AntBuildFileGroup> myGroups = new ArrayList<AntBuildFileGroup>();

  private final Project myProject;

  @Inject
  public AntBuildFileGroupManagerImpl(Project project) {
    myProject = project;
  }

  @Nonnull
  @Override
  public AntBuildFileGroup createGroup(@Nullable AntBuildFileGroup parent, @Nonnull String name) {
    AntBuildFileGroupImpl group = new AntBuildFileGroupImpl(name, parent);

    if (parent != null) {
      ((AntBuildFileGroupImpl)parent).getChildrenAsList().add(group);
    }
    else {
      myGroups.add(group);
    }
    return group;
  }

  @Override
  public void moveToGroup(@Nonnull AntBuildFile file, @Nullable AntBuildFileGroup group) {
    for (Map.Entry<AntBuildFileGroup, List<consulo.virtualFileSystem.VirtualFile>> entry : myFileGroupList.entrySet()) {
      if (entry.getValue().contains(file.getVirtualFile())) {
        entry.getValue().remove(file.getVirtualFile());
        break;
      }
    }

    if (group == null) {
      return;
    }
    List<consulo.virtualFileSystem.VirtualFile> virtualFiles = myFileGroupList.get(group);
    if (virtualFiles == null) {
      myFileGroupList.put(group, virtualFiles = new ArrayList<VirtualFile>());
    }
    virtualFiles.add(file.getVirtualFile());
  }

  @Override
  public AntBuildFile[] getFilesForGroup(@Nonnull AntBuildFileGroup group) {
    final List<VirtualFile> virtualFiles = myFileGroupList.get(group);
    if (virtualFiles == null || virtualFiles.isEmpty()) {
      return AntBuildFile.EMPTY_ARRAY;
    }
    List<AntBuildFile> files = new ArrayList<AntBuildFile>();
    final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
    final PsiManager manager = consulo.language.psi.PsiManager.getInstance(myProject);
    for (consulo.virtualFileSystem.VirtualFile virtualFile : virtualFiles) {
      final PsiFile file = manager.findFile(virtualFile);
      if (!(file instanceof XmlFile)) {
        continue;
      }
      final AntBuildFileBase antBuildFile = antConfiguration.getAntBuildFile(file);
      if (antBuildFile == null) {
        continue;
      }
      files.add(antBuildFile);
    }

    return files.toArray(new AntBuildFile[files.size()]);
  }

  @Nonnull
  @Override
  public AntBuildFileGroup[] getFirstLevelGroups() {
    return myGroups.isEmpty() ? AntBuildFileGroup.EMPTY_ARRAY : myGroups.toArray(new AntBuildFileGroup[myGroups.size()]);
  }

  @Override
  public AntBuildFileGroup findGroup(@Nonnull AntBuildFile buildFile) {
    for (Map.Entry<AntBuildFileGroup, List<consulo.virtualFileSystem.VirtualFile>> entry : myFileGroupList.entrySet()) {
      if (entry.getValue().contains(buildFile.getVirtualFile())) {
        return entry.getKey();
      }
    }
    return null;
  }

  @Override
  public void removeGroup(@Nonnull AntBuildFileGroup buildGroup) {
    myFileGroupList.remove(buildGroup);
    myGroups.remove(buildGroup);

    final AntBuildFileGroup parent = buildGroup.getParent();
    if (parent != null) {
      ((AntBuildFileGroupImpl)parent).getChildrenAsList().remove(buildGroup);
    }

    for (AntBuildFileGroup child : buildGroup.getChildren()) {
      removeGroup(child);
    }
  }

  @Nullable
  @Override
  public Element getState() {
    Element groupsElement = new Element("ant-groups");
    for (AntBuildFileGroup group : myGroups) {
      writeToElement(group, groupsElement);
    }
    return groupsElement;
  }

  private void writeToElement(AntBuildFileGroup group, Element parent) {
    final Element element = new Element("ant-group");
    element.setAttribute("name", group.getName());

    for (AntBuildFileGroup childGroup : group.getChildren()) {
      writeToElement(childGroup, element);
    }

    for (AntBuildFile file : getFilesForGroup(group)) {
      final Element fileElement = new Element("file");
      fileElement.setText(file.getVirtualFile().getUrl());
      element.addContent(fileElement);
    }

    parent.addContent(element);
  }

  @Override
  public void loadState(Element state) {
    final List<Element> children = state.getChildren("ant-group");
    for (Element child : children) {
      readElement(null, child);
    }
  }

  private void readElement(AntBuildFileGroup parentGroup, Element child) {
    String name = child.getAttributeValue("name");

    final AntBuildFileGroup newChildGroup = createGroup(parentGroup, name);
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();

    final List<Element> children = child.getChildren();
    for (Element element : children) {
      String elementName = element.getName();
      if (elementName.equals("ant-group")) {
        readElement(newChildGroup, element);
      }
      else if (elementName.equals("file")) {
        consulo.virtualFileSystem.VirtualFile virtualFile = vfManager.findFileByUrl(element.getText());
        if (virtualFile != null) {
          List<consulo.virtualFileSystem.VirtualFile> virtualFiles = myFileGroupList.get(newChildGroup);
          if (virtualFiles == null) {
            myFileGroupList.put(newChildGroup, virtualFiles = new ArrayList<consulo.virtualFileSystem.VirtualFile>());
          }
          virtualFiles.add(virtualFile);
        }
      }
    }
  }
}
