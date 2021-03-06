/*
 * Copyright 2013-2017 consulo.io
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
package consulo.web;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.idea.ApplicationStarter;
import com.intellij.idea.Main;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import consulo.application.ApplicationProperties;
import consulo.web.servlet.RootUIBuilder;
import consulo.web.servlet.ui.UIIconServlet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.Servlet;
import javax.swing.*;
import java.io.File;

/**
 * @author VISTALL
 * @since 16-May-17
 */
public class WebLoader {
  static class Factory implements Logger.Factory {
    @Override
    public Logger getLoggerInstance(String category) {
      return new Logger() {
        @Override
        public boolean isDebugEnabled() {
          return false;
        }

        @Override
        public void debug(@NonNls String message) {

        }

        @Override
        public void debug(@Nullable Throwable t) {

        }

        @Override
        public void debug(@NonNls String message, @Nullable Throwable t) {

        }

        @Override
        public void error(@NonNls String message, @Nullable Throwable t, @NonNls String... details) {
          System.out.println(message);
          if (t != null) {
            t.printStackTrace();
          }
        }

        @Override
        public void info(@NonNls String message) {
          System.out.println(message);
        }

        @Override
        public void info(@NonNls String message, @Nullable Throwable t) {
          System.out.println(message);
          if (t != null) {
            t.printStackTrace();
          }
        }

        @Override
        public void warn(@NonNls String message, @Nullable Throwable t) {
          System.out.println(message);
          if (t != null) {
            t.printStackTrace();
          }
        }
      };
    }
  }

  public void start(String[] args) {
    File home = new File(args[0]);

    System.setProperty("java.awt.headless", "true");
    System.setProperty(PathManager.PROPERTY_HOME_PATH, home.getPath());
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, home.getPath() + "/.config/sandbox/config");
    System.setProperty(PathManager.PROPERTY_SYSTEM_PATH, home.getPath() + "/.config/sandbox/system");
    System.setProperty(ApplicationProperties.CONSULO_PLUGINS_PATHS, new File(home, "plugins").getPath());
    System.setProperty(ApplicationProperties.CONSULO_AS_WEB_APP, Boolean.TRUE.toString());

    Main.setFlags(new String[0]);

    StartupUtil.prepareAndStart(args, (newConfigFolder, commandLineArgs) -> {
      ApplicationStarter app = new ApplicationStarter(commandLineArgs);

      SwingUtilities.invokeLater(() -> {
        PluginManager.installExceptionHandler();
        app.run(newConfigFolder);
      });
    });
  }

  public void destroy() {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      Disposer.dispose(application);
    }
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public Class<? extends Servlet>[] getServletClasses() {
    return new Class[]{RootUIBuilder.Servlet.class, UIIconServlet.class};
  }
}
