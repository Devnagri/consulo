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
package consulo.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

/**
 * @author VISTALL
 * @since 12-Sep-17
 */
public interface Tree<E> extends Component {
  interface SelectListener<K> extends EventListener {
    @RequiredUIAccess
    void onSelected(TreeNode<K> node);
  }

  @Nullable
  TreeNode<E> getSelectedNode();

  void expand(@NotNull TreeNode<E> node);

  @NotNull
  default Runnable addSelectListener(@NotNull @RequiredUIAccess SelectListener<E> listener) {
    return addListener(SelectListener.class, listener);
  }
}
