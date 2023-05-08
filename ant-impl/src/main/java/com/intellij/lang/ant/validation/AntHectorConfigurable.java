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
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntImportsIndex;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.dom.AntDomFileDescription;
import consulo.configurable.ConfigurationException;
import consulo.language.editor.HectorComponentPanel;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.project.Project;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import consulo.xml.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: May 12, 2008
 */
public class AntHectorConfigurable extends HectorComponentPanel {
  @NonNls
  private static final String NONE = "<None>";
  @NonNls 
  public static final String CONTEXTS_COMBO_KEY = "AntContextsComboBox";

  private final XmlFile myFile;
  private final String myLocalPath;
  private final Map<String, XmlFile> myPathToFileMap = new HashMap<String, XmlFile>();
  private String myOriginalContext = NONE;
  
  private JComboBox myCombo;
  private final consulo.language.psi.scope.GlobalSearchScope myFileFilter;
  private final Project myProject;

  public AntHectorConfigurable(XmlFile file) {
    myFile = file;
    myProject = file.getProject();
    final consulo.virtualFileSystem.VirtualFile selfVFile = myFile.getVirtualFile();
    myLocalPath = VirtualFilePathUtil.getLocalPath(selfVFile);
    myFileFilter = GlobalSearchScope.projectScope(myProject);
  }

  public boolean canClose() {
    return !myCombo.isPopupVisible();
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder("File Context", false));
    myCombo = new ComboBox();
    myCombo.putClientProperty(CONTEXTS_COMBO_KEY, Boolean.TRUE);
    panel.add(
        new JLabel("Included into:"), 
        new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 5, 0), 0, 0)
    );
    panel.add(
      myCombo,
      new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 0), 0, 0));

    final PsiManager psiManager = consulo.language.psi.PsiManager.getInstance(myProject);
    final FileBasedIndex fbi = FileBasedIndex.getInstance();
    final Collection<VirtualFile> antFiles = fbi.getContainingFiles(AntImportsIndex.INDEX_NAME, AntImportsIndex.ANT_FILES_WITH_IMPORTS_KEY, myFileFilter);
    
    for (consulo.virtualFileSystem.VirtualFile file : antFiles) {
      final PsiFile psiFile = psiManager.findFile(file);
      if (!(psiFile instanceof XmlFile)) {
        continue;
      }
      final XmlFile xmlFile = (XmlFile)psiFile;
      if (!xmlFile.equals(myFile) && AntDomFileDescription.isAntFile(xmlFile)) {
        final String path = VirtualFilePathUtil.getLocalPath(file);
        final XmlFile previous = myPathToFileMap.put(path, xmlFile);
        assert previous == null;
      }
    }

    final List<String> paths = new ArrayList<String>(myPathToFileMap.keySet());
    Collections.sort(paths, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareTo(o2);
      }
    });

    myCombo.addItem(NONE);
    for (String path : paths) {
      myCombo.addItem(path);
    }

    final AntConfigurationBase antConfig = AntConfigurationBase.getInstance(myProject);
    final XmlFile currentContext = antConfig.getContextFile(myFile);
    if (currentContext != null) {
      final consulo.virtualFileSystem.VirtualFile vFile = currentContext.getVirtualFile();
      
      assert vFile != null;

      final String path = VirtualFilePathUtil.getLocalPath(vFile);
      if (!FileUtil.pathsEqual(path, myLocalPath)) {
        myOriginalContext = path;
      }
    }
    myCombo.setSelectedItem(myOriginalContext);

    return panel;
  }

  public boolean isModified() {
    return !FileUtil.pathsEqual(myOriginalContext, (String)myCombo.getSelectedItem());
  }

  public void apply() throws ConfigurationException
  {
    applyItem((String)myCombo.getSelectedItem());
  }

  public void reset() {
    applyItem(myOriginalContext);
  }

  private void applyItem(final String contextStr) {
    XmlFile context = null;
    if (!NONE.equals(contextStr)) {
      context = myPathToFileMap.get(contextStr);
      assert context != null;
    }
    AntConfigurationBase.getInstance(myProject).setContextFile(myFile, context);
  }

  public void disposeUIResources() {
    myPathToFileMap.clear();
  }
}
