// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.shaking.DiscardedChecker;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerFactory;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.shaking.rootset.RootSet;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/** Shared main-dex and whole-program keep/discard diagnostics. */
final class CheckDiscardedSupport {

  private CheckDiscardedSupport() {}

  static void processWhyAreYouKeepingAndCheckDiscarded(
      RootSet rootSet,
      Supplier<Collection<DexProgramClass>> classes,
      WhyAreYouKeepingConsumer whyAreYouKeepingConsumer,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      boolean forMainDex,
      InternalOptions options,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    if (whyAreYouKeepingConsumer != null) {
      for (DexReference reference : rootSet.reasonAsked) {
        whyAreYouKeepingConsumer.printWhyAreYouKeeping(
            enqueuer.getGraphReporter().getGraphNode(reference), System.out);
      }
    }
    DiscardedChecker discardedChecker =
        forMainDex ? DiscardedChecker.createForMainDex(appView) : DiscardedChecker.create(appView);
    List<ProgramDefinition> failed = discardedChecker.run(classes.get(), executorService);
    if (appView.options().testing.dontReportFailingCheckDiscarded) {
      assert !failed.isEmpty();
      return;
    }
    if (failed.isEmpty()) {
      return;
    }
    // If there is no kept-graph info, re-run the enqueueing to compute it.
    if (whyAreYouKeepingConsumer == null) {
      whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(null);
      ImmediateAppSubtypingInfo subtypingInfo = ImmediateAppSubtypingInfo.create(appView);
      if (forMainDex) {
        enqueuer =
            EnqueuerFactory.createForFinalMainDexTracing(
                appView, executorService, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceMainDex(executorService, timing);
      } else {
        enqueuer =
            EnqueuerFactory.createForWhyAreYouKeeping(
                appView, executorService, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceApplication(rootSet, executorService, timing);
      }
    }
    options.reporter.error(
        new CheckDiscardDiagnostic.Builder()
            .addFailedItems(failed, enqueuer.getGraphReporter(), whyAreYouKeepingConsumer)
            .build());
    options.reporter.failIfPendingErrors();
  }
}
