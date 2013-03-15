package com.jetbrains.crucible.ui.toolWindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * User : ktisha
 */
public class ChangesBrowserChangeNode extends ChangesBrowserNode<Change> implements TreeLinkMouseListener.HaveTooltip {
  private final Project myProject;
  private final ChangeNodeDecorator myDecorator;

  protected ChangesBrowserChangeNode(final Project project, Change userObject, @Nullable final ChangeNodeDecorator decorator) {
    super(userObject);
    myProject = project;
    myDecorator = decorator;
    if (!ChangesUtil.getFilePath(userObject).isDirectory()) {
      myCount = 1;
    }
  }

  @Override
  protected boolean isDirectory() {
    return ChangesUtil.getFilePath(getUserObject()).isDirectory();
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final Change change = getUserObject();
    final FilePath filePath = ChangesUtil.getFilePath(change);
    final String fileName = filePath.getName();

    if (myDecorator != null) {
      myDecorator.preDecorate(change, renderer, renderer.isShowFlatten());
    }

    final Color changeColor = change.getFileStatus().getColor();
    renderer.append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, changeColor));

    final String originText = change.getOriginText(myProject);
    if (originText != null) {
      renderer.append(" " + originText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    if (renderer.isShowFlatten()) {
      final File parentFile = filePath.getIOFile().getParentFile();
      if (parentFile != null) {
        renderer.append(" (" + parentFile.getPath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      appendSwitched(renderer);
    }
    else if (getCount() != 1 || getDirectoryCount() != 0) {
      appendSwitched(renderer);
      appendCount(renderer);
    }
    else {
      appendSwitched(renderer);
    }

    final Icon addIcon = change.getAdditionalIcon();
    if (addIcon != null) {
      renderer.setIcon(addIcon);
    } else {
      if (filePath.isDirectory() || !isLeaf()) {
        renderer.setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
      }
      else {
        renderer.setIcon(filePath.getFileType().getIcon());
      }
    }

    if (myDecorator != null) {
      myDecorator.decorate(change, renderer, renderer.isShowFlatten());
    }
  }

  private void appendSwitched(final ChangesBrowserNodeRenderer renderer) {
    final VirtualFile virtualFile = ChangesUtil.getFilePath(getUserObject()).getVirtualFile();
    if (virtualFile != null && ! myProject.isDefault()) {
      String branch = ChangeListManager.getInstance(myProject).getSwitchedBranch(virtualFile);
      if (branch != null) {
        renderer.append(" [switched to " + branch + "]", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  public String getTooltip() {
    final Change change = getUserObject();
    return change.getDescription();
  }

  @Override
  public String getTextPresentation() {
    return ChangesUtil.getFilePath(getUserObject()).getName();
  }

  @Override
  public String toString() {
    return FileUtil.toSystemDependentName(ChangesUtil.getFilePath(getUserObject()).getPath());
  }

  public int getSortWeight() {
    return 6;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof Change) {
      return ChangesUtil.getFilePath(getUserObject()).getName().compareToIgnoreCase(ChangesUtil.getFilePath((Change)o2).getName());
    }
    return 0;
  }
}
