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
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

public interface IdeFrameEx extends IdeFrame {
  default boolean isInFullScreen() {
    return false;
  }

  @NotNull
  default ActionCallback toggleFullScreen(boolean state) {
    return ActionCallback.REJECTED;
  }

  default void updateView() {
  }
}
