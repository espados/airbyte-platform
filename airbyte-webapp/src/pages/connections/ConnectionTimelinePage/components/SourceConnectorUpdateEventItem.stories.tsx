import { StoryObj } from "@storybook/react";

import { ConnectorIds } from "area/connector/utils";
import { ConnectionEventType } from "core/api/types/AirbyteClient";

import { SourceConnectorUpdateEventItem } from "./SourceConnectorUpdateEventItem";

export default {
  title: "connection-timeline/SourceConnectorUpdateEventItem",
  component: SourceConnectorUpdateEventItem,
} as StoryObj<typeof SourceConnectorUpdateEventItem>;

const baseEvent = {
  id: "fc62442f-1cc1-4a57-a385-11ca7507c649",
  connectionId: "a90ab3d6-b5cb-4e43-8c97-5a4ab8f2f7d9",
  eventType: ConnectionEventType.CONNECTOR_UPDATE,
  summary: {
    name: "PokeAPI",
    sourceDefinitionId: ConnectorIds.Sources.PokeApi,
  },
  user: {
    id: "00000000-0000-0000-0000-000000000000",
    email: "volodymyr.s.petrov@globallogic.com",
    name: "Volodymyr Petrov",
  },
  createdAt: 1732114841,
};
export const UpgradedVersionByUser: StoryObj<typeof SourceConnectorUpdateEventItem> = {
  args: {
    event: {
      ...baseEvent,
      summary: {
        ...baseEvent.summary,
        newDockerImageTag: "2.1.0",
        oldDockerImageTag: "2.0.5",
        changeReason: "USER",
      },
    },
  },
};

export const UpgradedVersionBySystem: StoryObj<typeof SourceConnectorUpdateEventItem> = {
  args: {
    event: {
      ...baseEvent,
      summary: {
        ...baseEvent.summary,
        newDockerImageTag: "2.1.0",
        oldDockerImageTag: "2.0.5",
        changeReason: "SYSTEM",
      },
    },
  },
};

export const DowngradedVersionByUser: StoryObj<typeof SourceConnectorUpdateEventItem> = {
  args: {
    event: {
      ...baseEvent,
      summary: {
        ...baseEvent.summary,
        newDockerImageTag: "1.0.1",
        oldDockerImageTag: "3.0.0",
        changeReason: "USER",
      },
    },
  },
};

export const DowngradedVersionBySystem: StoryObj<typeof SourceConnectorUpdateEventItem> = {
  args: {
    event: {
      ...baseEvent,
      summary: {
        ...baseEvent.summary,
        newDockerImageTag: "1.0.1",
        oldDockerImageTag: "3.0.0",
        changeReason: "SYSTEM",
      },
    },
  },
};

export const UpdatedVersionByUser: StoryObj<typeof SourceConnectorUpdateEventItem> = {
  args: {
    event: {
      ...baseEvent,
      summary: {
        ...baseEvent.summary,
        newDockerImageTag: "dev",
        oldDockerImageTag: "3.0.1",
        changeReason: "USER",
      },
    },
  },
};
