/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ui.internal.icon;

import com.intellij.openapi.util.IconLoader;
import consulo.ui.image.Image;
import consulo.ui.internal.SwingIconWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;

/**
 * @author VISTALL
 * @since 13-Jun-16
 */
public class DesktopImageImpl implements Image, SwingIconWrapper {
  private Icon myIcon;

  public DesktopImageImpl(URL url) {
    myIcon = IconLoader.findIcon(url);
  }

  @Override
  public int getHeight() {
    return myIcon.getIconHeight();
  }

  @Override
  public int getWidth() {
    return myIcon.getIconWidth();
  }

  @NotNull
  @Override
  public Icon toSwingIcon() {
    return myIcon;
  }
}
