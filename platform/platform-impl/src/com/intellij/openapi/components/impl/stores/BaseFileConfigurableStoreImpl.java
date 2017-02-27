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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

abstract class BaseFileConfigurableStoreImpl extends ComponentStoreImpl {
  @NonNls
  public static final String ATTRIBUTE_NAME = "name";

  private static final List<String> ourConversionProblemsStorage = new SmartList<String>();

  private StateStorageManager myStateStorageManager;
  protected final PathMacroManager myPathMacroManager;

  protected BaseFileConfigurableStoreImpl(@NotNull PathMacroManager pathMacroManager) {
    myPathMacroManager = pathMacroManager;
  }

  @NotNull
  protected abstract XmlElementStorage getMainStorage();

  @Nullable
  static List<String> getConversionProblemsStorage() {
    return ourConversionProblemsStorage;
  }

  @Override
  public void load() throws IOException, StateStorageException {
    getMainStorageData(); //load it
  }

  public StorageData getMainStorageData() {
    return getMainStorage().getStorageData();
  }

  @NotNull
  @Override
  protected final PathMacroManager getPathMacroManagerForDefaults() {
    return myPathMacroManager;
  }

  @NotNull
  @Override
  public final StateStorageManager getStateStorageManager() {
    if (myStateStorageManager == null) {
      myStateStorageManager = createStateStorageManager();
    }
    return myStateStorageManager;
  }

  @NotNull
  protected abstract StateStorageManager createStateStorageManager();
}
