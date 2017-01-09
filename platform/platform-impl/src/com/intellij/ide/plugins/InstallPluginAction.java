/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import consulo.annotations.RequiredDispatchThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author lloix
 */
public class InstallPluginAction extends AnAction implements DumbAware {
  private static final Set<IdeaPluginDescriptor> ourInstallingNodes = new HashSet<>();

  private final PluginManagerMain myInstalledPluginPanel;
  private final PluginManagerMain myAvailablePluginPanel;

  public InstallPluginAction(PluginManagerMain availablePluginPanel, PluginManagerMain installedPluginPanel) {
    super(IdeBundle.message("action.download.and.install.plugin"), IdeBundle.message("action.download.and.install.plugin"), AllIcons.Actions.Install);
    myAvailablePluginPanel = availablePluginPanel;
    myInstalledPluginPanel = installedPluginPanel;
  }

  @RequiredDispatchThread
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    IdeaPluginDescriptor[] selection = getPluginTable().getSelectedObjects();
    boolean enabled = (selection != null);

    if (enabled) {
      for (IdeaPluginDescriptor descr : selection) {
        presentation.setText(IdeBundle.message("action.download.and.install.plugin"));
        presentation.setDescription(IdeBundle.message("action.download.and.install.plugin"));
        enabled &= !ourInstallingNodes.contains(descr);
        if (descr instanceof PluginNode) {
          enabled &= !PluginManagerColumnInfo.isDownloaded((PluginNode)descr);
          if (((PluginNode)descr).getStatus() == PluginNode.STATUS_INSTALLED) {
            presentation.setText(IdeBundle.message("action.update.plugin"));
            presentation.setDescription(IdeBundle.message("action.update.plugin"));
            enabled &= InstalledPluginsTableModel.hasNewerVersion(descr.getPluginId());
          }
        }
        else if (descr instanceof IdeaPluginDescriptorImpl) {
          presentation.setText(IdeBundle.message("action.update.plugin"));
          presentation.setDescription(IdeBundle.message("action.update.plugin"));
          PluginId id = descr.getPluginId();
          enabled = enabled && InstalledPluginsTableModel.hasNewerVersion(id);
        }
      }
    }

    presentation.setEnabled(enabled);
  }

  @RequiredDispatchThread
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    install(null);
  }

  public void install(@Nullable final Runnable onSuccess) {
    IdeaPluginDescriptor[] selection = getPluginTable().getSelectedObjects();

    if (userConfirm(selection)) {
      final ArrayList<PluginNode> list = new ArrayList<>();
      for (IdeaPluginDescriptor descr : selection) {
        PluginNode pluginNode = null;
        if (descr instanceof PluginNode) {
          pluginNode = (PluginNode)descr;
        }
        else if (descr instanceof IdeaPluginDescriptorImpl) {
          final PluginId pluginId = descr.getPluginId();
          pluginNode = new PluginNode(pluginId);
          pluginNode.setName(descr.getName());
          pluginNode.addDependency(descr.getDependentPluginIds());
          pluginNode.addOptionalDependency(descr.getOptionalDependentPluginIds());
          pluginNode.setSize("-1");
        }

        if (pluginNode != null) {
          list.add(pluginNode);
          ourInstallingNodes.add(pluginNode);
        }
        final InstalledPluginsTableModel pluginsModel = (InstalledPluginsTableModel)myInstalledPluginPanel.getPluginsModel();
        final Set<IdeaPluginDescriptor> disabled = new HashSet<>();
        final Set<IdeaPluginDescriptor> disabledDependants = new HashSet<>();
        for (PluginNode node : list) {
          final PluginId pluginId = node.getPluginId();
          if (pluginsModel.isDisabled(pluginId)) {
            disabled.add(node);
          }
          for (PluginId dependantId : node.getDependentPluginIds()) {
            final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(dependantId);
            if (pluginDescriptor != null && pluginsModel.isDisabled(dependantId)) {
              disabledDependants.add(pluginDescriptor);
            }
          }
        }
        if (suggestToEnableInstalledPlugins(pluginsModel, disabled, disabledDependants, list)) {
          myInstalledPluginPanel.setRequireShutdown(true);
        }
      }
      try {
        final Consumer<Set<PluginNode>> onInstallRunnable = pluginNodes -> {
          UIUtil.invokeLaterIfNeeded(() -> installedPluginsToModel(pluginNodes));

          if (!myInstalledPluginPanel.isDisposed()) {
            UIUtil.invokeLaterIfNeeded(() -> {
              getPluginTable().updateUI();
              myInstalledPluginPanel.setRequireShutdown(true);
            });
          }
          else {
            boolean needToRestart = false;
            for (PluginNode node : pluginNodes) {
              final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(node.getPluginId());
              if (pluginDescriptor == null || pluginDescriptor.isEnabled()) {
                needToRestart = true;
                break;
              }
            }

            if (needToRestart) {
              PluginManagerMain.notifyPluginsWereInstalled(pluginNodes, null);
            }
          }

          if (onSuccess != null) {
            onSuccess.run();
          }
        };
        downloadPlugins(list, myAvailablePluginPanel.getPluginsModel().getAllPlugins(), onInstallRunnable, pluginNodes -> ourInstallingNodes.removeAll(list));
      }
      catch (final IOException e1) {
        ourInstallingNodes.removeAll(list);
        PluginManagerMain.LOG.error(e1);
        SwingUtilities.invokeLater(() -> IOExceptionDialog.showErrorDialog(IdeBundle.message("action.download.and.install.plugin"), IdeBundle.message("error.plugin.download.failed")));
      }
    }
  }

  private static void downloadPlugins(@NotNull final List<PluginNode> plugins,
                                      @NotNull final List<IdeaPluginDescriptor> allPlugins,
                                      @NotNull final Consumer<Set<PluginNode>> onSuccess,
                                      @Nullable final Consumer<Set<PluginNode>> cleanup) throws IOException {
    Task.Backgroundable.queue(null, IdeBundle.message("progress.download.plugins"), true, indicator -> {
      Set<PluginNode> set = null;
      try {
        set = PluginInstaller.prepareToInstall(plugins, allPlugins);
        if (set != null) {
          onSuccess.consume(set);
        }
      }
      finally {
        if (cleanup != null) cleanup.consume(set);
      }
    });
  }

  private static boolean suggestToEnableInstalledPlugins(final InstalledPluginsTableModel pluginsModel,
                                                         final Set<IdeaPluginDescriptor> disabled,
                                                         final Set<IdeaPluginDescriptor> disabledDependants,
                                                         final ArrayList<PluginNode> list) {
    if (!disabled.isEmpty() || !disabledDependants.isEmpty()) {
      String message = "";
      if (disabled.size() == 1) {
        message += "Updated plugin '" + disabled.iterator().next().getName() + "' is disabled.";
      }
      else if (!disabled.isEmpty()) {
        message += "Updated plugins " + StringUtil.join(disabled, IdeaPluginDescriptor::getName, ", ") + " are disabled.";
      }

      if (!disabledDependants.isEmpty()) {
        message += "<br>";
        message += "Updated plugin" + (list.size() > 1 ? "s depend " : " depends ") + "on disabled";
        if (disabledDependants.size() == 1) {
          message += " plugin '" + disabledDependants.iterator().next().getName() + "'.";
        }
        else {
          message += " plugins " + StringUtil.join(disabledDependants, IdeaPluginDescriptor::getName, ", ") + ".";
        }
      }
      message += " Disabled plugins and plugins which depends on disabled plugins won't be activated after restart.";

      int result;
      if (!disabled.isEmpty() && !disabledDependants.isEmpty()) {
        result = Messages.showYesNoCancelDialog(XmlStringUtil.wrapInHtml(message), CommonBundle.getWarningTitle(), "Enable all",
                                                "Enable updated plugin" + (disabled.size() > 1 ? "s" : ""), CommonBundle.getCancelButtonText(),
                                                Messages.getQuestionIcon());
        if (result == Messages.CANCEL) return false;
      }
      else {
        message += "<br>Would you like to enable ";
        if (!disabled.isEmpty()) {
          message += "updated plugin" + (disabled.size() > 1 ? "s" : "");
        }
        else {
          //noinspection SpellCheckingInspection
          message += "plugin dependenc" + (disabledDependants.size() > 1 ? "ies" : "y");
        }
        message += "?</body></html>";
        result = Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getQuestionIcon());
        if (result == Messages.NO) return false;
      }

      if (result == Messages.YES) {
        disabled.addAll(disabledDependants);
        pluginsModel.enableRows(disabled.toArray(new IdeaPluginDescriptor[disabled.size()]), true);
      }
      else if (result == Messages.NO && !disabled.isEmpty()) {
        pluginsModel.enableRows(disabled.toArray(new IdeaPluginDescriptor[disabled.size()]), true);
      }
      return true;
    }
    return false;
  }

  private void installedPluginsToModel(Collection<PluginNode> list) {
    for (PluginNode pluginNode : list) {
      final String idString = pluginNode.getPluginId().getIdString();
      final PluginManagerUISettings pluginManagerUISettings = PluginManagerUISettings.getInstance();
      if (!pluginManagerUISettings.getInstalledPlugins().contains(idString)) {
        pluginManagerUISettings.getInstalledPlugins().add(idString);
      }
      pluginManagerUISettings.myOutdatedPlugins.remove(idString);
    }

    final InstalledPluginsTableModel installedPluginsModel = (InstalledPluginsTableModel)myInstalledPluginPanel.getPluginsModel();
    for (PluginNode node : list) {
      installedPluginsModel.appendOrUpdateDescriptor(node);
    }
  }


  public PluginTable getPluginTable() {
    return myAvailablePluginPanel.getPluginTable();
  }

  //---------------------------------------------------------------------------
  //  Show confirmation message depending on the amount and type of the
  //  selected plugin descriptors: already downloaded plugins need "update"
  //  while non-installed yet need "install".
  //---------------------------------------------------------------------------
  private boolean userConfirm(IdeaPluginDescriptor[] selection) {
    String message;
    if (selection.length == 1) {
      if (selection[0] instanceof IdeaPluginDescriptorImpl) {
        message = IdeBundle.message("prompt.update.plugin", selection[0].getName());
      }
      else {
        message = IdeBundle.message("prompt.download.and.install.plugin", selection[0].getName());
      }
    }
    else {
      message = IdeBundle.message("prompt.install.several.plugins", selection.length);
    }

    return Messages.showYesNoDialog(myAvailablePluginPanel.getMainPanel(), message, IdeBundle.message("action.download.and.install.plugin"), Messages.getQuestionIcon()) ==
           Messages.YES;
  }
}
