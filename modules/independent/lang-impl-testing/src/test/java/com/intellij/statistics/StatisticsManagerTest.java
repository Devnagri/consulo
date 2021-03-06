/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.statistics;

import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NonNls;
import consulo.annotations.RequiredDispatchThread;

/**
 * @author peter
 */
public class StatisticsManagerTest extends LightPlatformTestCase {
  @NonNls private static final String TEST_CONTEXT = "testContext";

  @RequiredDispatchThread
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(getTestRootDisposable());
  }

  private static void incUseCount(String value, int times) {
    for (int i = 0; i < times; i++) {
      StatisticsManager.getInstance().incUseCount(new StatisticsInfo(TEST_CONTEXT, value));
    }
  }

  private static int getUseCount(String value) {
    return StatisticsManager.getInstance().getUseCount(new StatisticsInfo(TEST_CONTEXT, value));
  }

  public void testIncUseCount() throws Throwable {
    incUseCount("b", 1);
    assertEquals(1, getUseCount("b"));
    assertEquals(0, getUseCount("c"));
    
    incUseCount("c", 1);
    assertEquals(1, getUseCount("b"));
    assertEquals(1, getUseCount("c"));
  }

  public void testFlexibility() throws Throwable {
    incUseCount("b", 100);
    incUseCount("c", 4);
    assertTrue(getUseCount("c") > getUseCount("b"));
  }

  public void testReturn() throws Throwable {
    incUseCount("b", 100);
    incUseCount("c", 4);
    incUseCount("b", 1);
    assertTrue(getUseCount("c") >= getUseCount("b"));
    incUseCount("b", 3);
    assertTrue(getUseCount("c") < getUseCount("b"));
  }

}
