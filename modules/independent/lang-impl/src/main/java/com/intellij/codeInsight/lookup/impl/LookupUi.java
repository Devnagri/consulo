/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.ShowHideIntentionIconLookupAction;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import consulo.ui.plaf.ScrollBarUIConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;

/**
 * @author peter
 */
class LookupUi {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupUi");
  @NotNull
  private final LookupImpl myLookup;
  private final Advertiser myAdvertiser;
  private final JBList myList;
  private final Project myProject;
  private final ModalityState myModalityState;
  private final Alarm myHintAlarm = new Alarm();
  private final JLabel mySortingLabel = new JLabel();
  private final JScrollPane myScrollPane;
  private final JButton myScrollBarIncreaseButton;
  private final AsyncProcessIcon myProcessIcon = new AsyncProcessIcon("Completion progress");
  private final JPanel myIconPanel = new JPanel(new BorderLayout());
  private final LookupLayeredPane myLayeredPane = new LookupLayeredPane();

  private LookupHint myElementHint = null;
  private int myMaximumHeight = Integer.MAX_VALUE;
  private Boolean myPositionedAbove = null;

  LookupUi(@NotNull LookupImpl lookup, Advertiser advertiser, JBList list, Project project) {
    myLookup = lookup;
    myAdvertiser = advertiser;
    myList = list;
    myProject = project;

    myIconPanel.setVisible(false);
    myIconPanel.setBackground(Color.LIGHT_GRAY);
    myIconPanel.add(myProcessIcon);

    JComponent adComponent = advertiser.getAdComponent();
    adComponent.setBorder(new EmptyBorder(0, 1, 1, 2 + AllIcons.Ide.LookupRelevance.getIconWidth()));
    myLayeredPane.mainPanel.add(adComponent, BorderLayout.SOUTH);

    myScrollBarIncreaseButton = new JButton();
    myScrollBarIncreaseButton.setFocusable(false);
    myScrollBarIncreaseButton.setRequestFocusEnabled(false);

    myScrollPane = new JBScrollPane(lookup.getList());
    myScrollPane.setViewportBorder(JBUI.Borders.empty());
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    UIUtil.putClientProperty(myScrollPane, ScrollBarUIConstants.INCREASE_BUTTON_FACTORY, () -> myScrollBarIncreaseButton);

    lookup.getComponent().add(myLayeredPane, BorderLayout.CENTER);

    //IDEA-82111
    fixMouseCheaters();

    myLayeredPane.mainPanel.add(myScrollPane, BorderLayout.CENTER);
    myScrollPane.setBorder(null);

    mySortingLabel.setBorder(new LineBorder(new JBColor(Color.LIGHT_GRAY, JBColor.background())));
    mySortingLabel.setOpaque(true);
    new ChangeLookupSorting().installOn(mySortingLabel);
    updateSorting();
    myModalityState = ModalityState.stateForComponent(lookup.getTopLevelEditor().getComponent());

    addListeners();

    updateScrollbarVisibility();

    Disposer.register(lookup, myProcessIcon);
    Disposer.register(lookup, myHintAlarm);
  }

  private void addListeners() {
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (myLookup.isLookupDisposed()) return;

        myHintAlarm.cancelAllRequests();

        final LookupElement item = myLookup.getCurrentItem();
        if (item != null) {
          updateHint(item);
        }
      }
    });

    final Alarm alarm = new Alarm(myLookup);
    myScrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        if (myLookup.myUpdating || myLookup.isLookupDisposed()) return;
        alarm.addRequest(new Runnable() {
          @Override
          public void run() {
            myLookup.refreshUi(false, false);
          }
        }, 300, myModalityState);
      }
    });
  }

  private void updateScrollbarVisibility() {
    boolean showSorting = myLookup.isCompletion() && myList.getModel().getSize() >= 3;
    mySortingLabel.setVisible(showSorting);
    myScrollPane.setVerticalScrollBarPolicy(showSorting ? ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS : ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
  }

  private void updateHint(@NotNull final LookupElement item) {
    myLookup.checkValid();
    if (myElementHint != null) {
      myLayeredPane.remove(myElementHint);
      myElementHint = null;
      final JRootPane rootPane = myLookup.getComponent().getRootPane();
      if (rootPane != null) {
        rootPane.revalidate();
        rootPane.repaint();
      }
    }
    if (!item.isValid()) {
      return;
    }

    final Collection<LookupElementAction> actions = myLookup.getActionsFor(item);
    if (!actions.isEmpty()) {
      myHintAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          if (!ShowHideIntentionIconLookupAction.shouldShowLookupHint() ||
              ((CompletionExtender)myList.getExpandableItemsHandler()).isShowing()) {
            return;
          }
          myElementHint = new LookupHint();
          myLayeredPane.add(myElementHint, 20, 0);
          myLayeredPane.layoutHint();
        }
      }, 500, myModalityState);
    }
  }

  //Yes, it's possible to move focus to the hint. It's inconvenient, it doesn't make sense, but it's possible.
  // This fix is for those jerks
  private void fixMouseCheaters() {
    myLookup.getComponent().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        final ActionCallback done = IdeFocusManager.getInstance(myProject).requestFocus(myLookup.getTopLevelEditor().getContentComponent(), true);
        IdeFocusManager.getInstance(myProject).typeAheadUntil(done);
        new Alarm(myLookup).addRequest(new Runnable() {
          @Override
          public void run() {
            if (!done.isDone()) {
              done.setDone();
            }
          }
        }, 300, myModalityState);
      }
    });
  }

  void setCalculating(final boolean calculating) {
    Runnable setVisible = new Runnable() {
      @Override
      public void run() {
        myIconPanel.setVisible(myLookup.isCalculating());
      }
    };
    if (myLookup.isCalculating()) {
      new Alarm(myLookup).addRequest(setVisible, 100, myModalityState);
    } else {
      setVisible.run();
    }

    if (calculating) {
      myProcessIcon.resume();
    } else {
      myProcessIcon.suspend();
    }
  }

  private void updateSorting() {
    final boolean lexi = UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
    mySortingLabel.setIcon(lexi ? AllIcons.Ide.LookupAlphanumeric : AllIcons.Ide.LookupRelevance);
    mySortingLabel.setToolTipText(lexi ? "Click to sort variants by relevance" : "Click to sort variants alphabetically");

    myLookup.resort(false);
  }

  void refreshUi(boolean selectionVisible, boolean itemsChanged, boolean reused, boolean onExplicitAction) {
    Editor editor = myLookup.getTopLevelEditor();
    if (editor.getComponent().getRootPane() == null || editor instanceof EditorWindow && !((EditorWindow)editor).isValid()) {
      return;
    }

    updateScrollbarVisibility();

    if (myLookup.myResizePending || itemsChanged) {
      myMaximumHeight = Integer.MAX_VALUE;
    }
    Rectangle rectangle = calculatePosition();
    myMaximumHeight = rectangle.height;

    if (myLookup.myResizePending || itemsChanged) {
      myLookup.myResizePending = false;
      myLookup.pack();
    }
    HintManagerImpl.updateLocation(myLookup, editor, rectangle.getLocation());

    if (reused || selectionVisible || onExplicitAction) {
      myLookup.ensureSelectionVisible(false);
    }
  }

  boolean isPositionedAboveCaret() {
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  // in layered pane coordinate system.
  Rectangle calculatePosition() {
    final JComponent lookupComponent = myLookup.getComponent();
    Dimension dim = lookupComponent.getPreferredSize();
    int lookupStart = myLookup.getLookupStart();
    Editor editor = myLookup.getTopLevelEditor();
    if (lookupStart < 0 || lookupStart > editor.getDocument().getTextLength()) {
      LOG.error(lookupStart + "; offset=" + editor.getCaretModel().getOffset() + "; element=" +
                myLookup.getPsiElement());
    }

    LogicalPosition pos = editor.offsetToLogicalPosition(lookupStart);
    Point location = editor.logicalPositionToXY(pos);
    location.y += editor.getLineHeight();
    location.x -= myLookup.myCellRenderer.getTextIndent();
    // extra check for other borders
    final Window window = UIUtil.getWindow(lookupComponent);
    if (window != null) {
      final Point point = SwingUtilities.convertPoint(lookupComponent, 0, 0, window);
      location.x -= point.x;
    }

    SwingUtilities.convertPointToScreen(location, editor.getContentComponent());
    final Rectangle screenRectangle = ScreenUtil.getScreenRectangle(location);

    if (!isPositionedAboveCaret()) {
      int shiftLow = screenRectangle.height - (location.y + dim.height);
      myPositionedAbove = shiftLow < 0 && shiftLow < location.y - dim.height && location.y >= dim.height;
    }
    if (isPositionedAboveCaret()) {
      location.y -= dim.height + editor.getLineHeight();
      if (pos.line == 0) {
        location.y += 1;
        //otherwise the lookup won't intersect with the editor and every editor's resize (e.g. after typing in console) will close the lookup
      }
    }

    if (!screenRectangle.contains(location)) {
      location = ScreenUtil.findNearestPointOnBorder(screenRectangle, location);
    }

    final JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane == null) {
      LOG.error("editor.disposed=" + editor.isDisposed() + "; lookup.disposed=" + myLookup.isLookupDisposed() + "; editorShowing=" + editor.getContentComponent().isShowing());
    }
    Rectangle candidate = new Rectangle(location, dim);
    ScreenUtil.cropRectangleToFitTheScreen(candidate);

    SwingUtilities.convertPointFromScreen(location, rootPane.getLayeredPane());
    myMaximumHeight = candidate.height;
    return new Rectangle(location.x, location.y, dim.width, candidate.height);
  }

  private class LookupLayeredPane extends JBLayeredPane {
    final JPanel mainPanel = new JPanel(new BorderLayout());

    private LookupLayeredPane() {
      add(mainPanel, 0, 0);
      add(myIconPanel, 42, 0);
      add(mySortingLabel, 10, 0);

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(@Nullable Container parent) {
          int maxCellWidth = myLookup.myLookupTextWidth + myLookup.myCellRenderer.getTextIndent();
          int scrollBarWidth = myScrollPane.getPreferredSize().width - myScrollPane.getViewport().getPreferredSize().width;
          int listWidth = Math.min(scrollBarWidth + maxCellWidth, UISettings.getInstance().MAX_LOOKUP_WIDTH2);

          Dimension adSize = myAdvertiser.getAdComponent().getPreferredSize();

          int panelHeight = myList.getPreferredScrollableViewportSize().height + adSize.height;
          if (myList.getModel().getSize() > myList.getVisibleRowCount() && myList.getVisibleRowCount() >= 5) {
            panelHeight -= myList.getFixedCellHeight() / 2;
          }
          return new Dimension(Math.max(listWidth, adSize.width), Math.min(panelHeight, myMaximumHeight));
        }

        @Override
        public void layoutContainer(Container parent) {
          Dimension size = getSize();
          mainPanel.setSize(size);
          mainPanel.validate();

          if (!myLookup.myResizePending) {
            Dimension preferredSize = preferredLayoutSize(null);
            if (preferredSize.width != size.width) {
              UISettings.getInstance().MAX_LOOKUP_WIDTH2 = Math.max(500, size.width);
            }

            int listHeight = myList.getLastVisibleIndex() - myList.getFirstVisibleIndex() + 1;
            if (listHeight != myList.getModel().getSize() && listHeight != myList.getVisibleRowCount() && preferredSize.height != size.height) {
              UISettings.getInstance().MAX_LOOKUP_LIST_HEIGHT = Math.max(5, listHeight);
            }
          }

          myList.setFixedCellWidth(myScrollPane.getViewport().getWidth());
          layoutStatusIcons();
          layoutHint();
        }
      });
    }

    private void layoutStatusIcons() {
      int adHeight = myAdvertiser.getAdComponent().getPreferredSize().height;
      Dimension buttonSize = adHeight > 0 || !mySortingLabel.isVisible() ? new Dimension(0, 0) : new Dimension(
              AllIcons.Ide.LookupRelevance.getIconWidth(), AllIcons.Ide.LookupRelevance.getIconHeight());
      myScrollBarIncreaseButton.setPreferredSize(buttonSize);
      myScrollBarIncreaseButton.setMinimumSize(buttonSize);
      myScrollBarIncreaseButton.setMaximumSize(buttonSize);
      JScrollBar vScrollBar = myScrollPane.getVerticalScrollBar();
      vScrollBar.revalidate();
      vScrollBar.repaint();

      final Dimension iconSize = myProcessIcon.getPreferredSize();
      myIconPanel.setBounds(getWidth() - iconSize.width - (vScrollBar.isVisible() ? vScrollBar.getWidth() : 0), 0, iconSize.width,
                            iconSize.height);

      final Dimension sortSize = mySortingLabel.getPreferredSize();
      final int sortWidth = vScrollBar.isVisible() ? vScrollBar.getWidth() : sortSize.width;
      final int sortHeight = Math.max(sortSize.height, adHeight);
      mySortingLabel.setBounds(getWidth() - sortWidth, getHeight() - sortHeight, sortSize.width, sortHeight);
    }

    void layoutHint() {
      if (myElementHint != null && myLookup.getCurrentItem() != null) {
        final Rectangle bounds = myLookup.getCurrentItemBounds();
        myElementHint.setSize(myElementHint.getPreferredSize());

        JScrollBar sb = myScrollPane.getVerticalScrollBar();
        int x = bounds.x + bounds.width - myElementHint.getWidth() + (sb.isVisible() ? sb.getWidth() : 0);
        x = Math.min(x, getWidth() - myElementHint.getWidth());
        myElementHint.setLocation(new Point(x, bounds.y));
      }
    }
  }

  private class LookupHint extends JLabel {
    private final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    private final Border ACTIVE_BORDER = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(1, 1, 1, 1));
    private LookupHint() {
      setOpaque(false);
      setBorder(INACTIVE_BORDER);
      setIcon(AllIcons.Actions.IntentionBulb);
      String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
              ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
      if (acceleratorsText.length() > 0) {
        setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          setBorder(ACTIVE_BORDER);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setBorder(INACTIVE_BORDER);
        }
        @Override
        public void mousePressed(MouseEvent e) {
          if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
            myLookup.showElementActions();
          }
        }
      });
    }
  }

  private class ChangeLookupSorting extends ClickListener {

    @Override
    public boolean onClick(@NotNull MouseEvent e, int clickCount) {
      DataContext context = DataManager.getInstance().getDataContext(mySortingLabel);
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(createSortingAction(true));
      group.add(createSortingAction(false));
      JBPopupFactory.getInstance().createActionGroupPopup("Change sorting", group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false).showInBestPositionFor(
              context);
      return true;
    }

    private AnAction createSortingAction(boolean checked) {
      boolean currentSetting = UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
      final boolean newSetting = checked ? currentSetting : !currentSetting;
      return new DumbAwareAction(newSetting ? "Sort lexicographically" : "Sort by relevance", null, checked ? PlatformIcons.CHECK_ICON : null) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CHANGE_SORTING);
          UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = newSetting;
          updateSorting();
        }
      };
    }
  }
}
