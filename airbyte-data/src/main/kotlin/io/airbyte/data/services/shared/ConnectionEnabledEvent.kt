/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.data.services.shared

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
class ConnectionEnabledEvent : ConnectionEvent {
  override fun getEventType(): ConnectionEvent.Type = ConnectionEvent.Type.CONNECTION_ENABLED
}
