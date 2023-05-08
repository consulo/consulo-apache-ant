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
package com.intellij.lang.ant.config.execution;

import consulo.apache.ant.ApacheAntIcons;
import consulo.application.AllIcons;
import consulo.ide.impl.idea.ui.MultilineTreeCellRenderer;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.SideBorder;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;

import javax.swing.*;

final class MessageTreeRenderer extends MultilineTreeCellRenderer {

  private MessageTreeRenderer() {
  }

  public static JScrollPane install(JTree tree) {
    JScrollPane scrollPane = MultilineTreeCellRenderer.installRenderer(tree, new MessageTreeRenderer());
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
    return scrollPane;
  }

  protected void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if(value instanceof MessageNode) {
      MessageNode messageNode = (MessageNode)value;
      setText(messageNode.getText(), messageNode.getTypeString() + messageNode.getPositionString());
    }
    else {
      String[] text = new String[] {value.toString()};
      if(text[0] == null) {
        text[0] = "";
      }
      setText(text, null);
    }

    Image icon = null;

    if (value instanceof MessageNode) {
      MessageNode node = (MessageNode)value;
      OldAntBuildMessageView.MessageType type = node.getType();
      if (type == OldAntBuildMessageView.MessageType.BUILD) {
        icon = ApacheAntIcons.AntInstallation;
      }
      else if (type == OldAntBuildMessageView.MessageType.TARGET) {
        icon = ApacheAntIcons.Target;
      }
      else if (type == OldAntBuildMessageView.MessageType.TASK) {
        icon = ApacheAntIcons.Task;
      }
      else if (type == OldAntBuildMessageView.MessageType.MESSAGE) {
        if (node.getPriority() == OldAntBuildMessageView.PRIORITY_WARN) {
          icon = AllIcons.General.Warning;
        }
        else {
          icon = AllIcons.General.Information;
        }
      }
      else if (type == OldAntBuildMessageView.MessageType.ERROR) {
        icon = AllIcons.General.Error;
      }
    }
    setIcon(TargetAWT.to(icon));
  }
}
