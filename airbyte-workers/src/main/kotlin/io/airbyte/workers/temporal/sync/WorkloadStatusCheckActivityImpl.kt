/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.temporal.sync

import io.airbyte.workers.sync.WorkloadClient
import jakarta.inject.Singleton

@Singleton
class WorkloadStatusCheckActivityImpl(
  private val workloadClient: WorkloadClient,
) : WorkloadStatusCheckActivity {
  override fun isTerminal(workloadId: String): Boolean = workloadClient.isTerminal(workloadId)
}
