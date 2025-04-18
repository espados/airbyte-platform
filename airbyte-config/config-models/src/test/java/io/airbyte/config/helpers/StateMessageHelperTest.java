/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.State;
import io.airbyte.config.StateType;
import io.airbyte.config.StateWrapper;
import io.airbyte.protocol.models.v0.AirbyteGlobalState;
import io.airbyte.protocol.models.v0.AirbyteStateMessage;
import io.airbyte.protocol.models.v0.AirbyteStateMessage.AirbyteStateType;
import io.airbyte.protocol.models.v0.AirbyteStreamState;
import io.airbyte.protocol.models.v0.StreamDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StateMessageHelperTest {

  @Test
  void testEmpty() {
    final Optional<StateWrapper> stateWrapper = StateMessageHelper.getTypedState(null);
    Assertions.assertThat(stateWrapper).isEmpty();
  }

  @Test
  void testEmptyList() {
    final Optional<StateWrapper> stateWrapper = StateMessageHelper.getTypedState(Jsons.arrayNode());
    Assertions.assertThat(stateWrapper).isEmpty();
  }

  @Test
  void testLegacy() {
    final Optional<StateWrapper> stateWrapper = StateMessageHelper.getTypedState(Jsons.emptyObject());
    Assertions.assertThat(stateWrapper).isNotEmpty();
    Assertions.assertThat(stateWrapper.get().getStateType()).isEqualTo(StateType.LEGACY);
  }

  @Test
  void testLegacyInList() {
    final JsonNode jsonState = Jsons.jsonNode(List.of(Map.of("Any", "value")));

    final Optional<StateWrapper> stateWrapper = StateMessageHelper.getTypedState(jsonState);
    Assertions.assertThat(stateWrapper).isNotEmpty();
    Assertions.assertThat(stateWrapper.get().getStateType()).isEqualTo(StateType.LEGACY);
    Assertions.assertThat(stateWrapper.get().getLegacyState()).isEqualTo(jsonState);
  }

  @Test
  void testLegacyInNewFormat() {
    final AirbyteStateMessage stateMessage = new AirbyteStateMessage()
        .withType(AirbyteStateType.LEGACY)
        .withData(Jsons.emptyObject());
    final Optional<StateWrapper> stateWrapper = StateMessageHelper.getTypedState(Jsons.jsonNode(List.of(stateMessage)));
    Assertions.assertThat(stateWrapper).isNotEmpty();
    Assertions.assertThat(stateWrapper.get().getStateType()).isEqualTo(StateType.LEGACY);
  }

  @Test
  void testGlobal() {
    final AirbyteStateMessage stateMessage = new AirbyteStateMessage()
        .withType(AirbyteStateType.GLOBAL)
        .withGlobal(
            new AirbyteGlobalState()
                .withSharedState(Jsons.emptyObject())
                .withStreamStates(List.of(
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("a")).withStreamState(Jsons.emptyObject()),
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("b")).withStreamState(Jsons.emptyObject()))));
    final Optional<StateWrapper> stateWrapper =
        StateMessageHelper.getTypedState(Jsons.jsonNode(List.of(stateMessage)));
    Assertions.assertThat(stateWrapper).isNotEmpty();
    Assertions.assertThat(stateWrapper.get().getStateType()).isEqualTo(StateType.GLOBAL);
    Assertions.assertThat(stateWrapper.get().getGlobal()).isEqualTo(stateMessage);
  }

  @Test
  void testStream() {
    final AirbyteStateMessage stateMessage1 = new AirbyteStateMessage()
        .withType(AirbyteStateType.STREAM)
        .withStream(
            new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("a")).withStreamState(Jsons.emptyObject()));
    final AirbyteStateMessage stateMessage2 = new AirbyteStateMessage()
        .withType(AirbyteStateType.STREAM)
        .withStream(
            new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("b")).withStreamState(Jsons.emptyObject()));
    final Optional<StateWrapper> stateWrapper =
        StateMessageHelper.getTypedState(Jsons.jsonNode(List.of(stateMessage1, stateMessage2)));
    Assertions.assertThat(stateWrapper).isNotEmpty();
    Assertions.assertThat(stateWrapper.get().getStateType()).isEqualTo(StateType.STREAM);
    Assertions.assertThat(stateWrapper.get().getStateMessages()).containsExactlyInAnyOrder(stateMessage1, stateMessage2);
  }

  @Test
  void testInvalidMixedState() {
    final AirbyteStateMessage stateMessage1 = new AirbyteStateMessage()
        .withType(AirbyteStateType.STREAM)
        .withStream(
            new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("a")).withStreamState(Jsons.emptyObject()));
    final AirbyteStateMessage stateMessage2 = new AirbyteStateMessage()
        .withType(AirbyteStateType.GLOBAL)
        .withGlobal(
            new AirbyteGlobalState()
                .withSharedState(Jsons.emptyObject())
                .withStreamStates(List.of(
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("a")).withStreamState(Jsons.emptyObject()),
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("b")).withStreamState(Jsons.emptyObject()))));
    Assertions
        .assertThatThrownBy(
            () -> StateMessageHelper.getTypedState(Jsons.jsonNode(List.of(stateMessage1, stateMessage2))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testDuplicatedGlobalState() {
    final AirbyteStateMessage stateMessage1 = new AirbyteStateMessage()
        .withType(AirbyteStateType.GLOBAL)
        .withGlobal(
            new AirbyteGlobalState()
                .withSharedState(Jsons.emptyObject())
                .withStreamStates(List.of(
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("a")).withStreamState(Jsons.emptyObject()),
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("b")).withStreamState(Jsons.emptyObject()))));
    final AirbyteStateMessage stateMessage2 = new AirbyteStateMessage()
        .withType(AirbyteStateType.GLOBAL)
        .withGlobal(
            new AirbyteGlobalState()
                .withSharedState(Jsons.emptyObject())
                .withStreamStates(List.of(
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("a")).withStreamState(Jsons.emptyObject()),
                    new AirbyteStreamState().withStreamDescriptor(new StreamDescriptor().withName("b")).withStreamState(Jsons.emptyObject()))));
    Assertions
        .assertThatThrownBy(
            () -> StateMessageHelper.getTypedState(Jsons.jsonNode(List.of(stateMessage1, stateMessage2))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testLegacyStateConversion() {
    final StateWrapper stateWrapper = new StateWrapper()
        .withStateType(StateType.LEGACY)
        .withLegacyState(Jsons.deserialize("{\"json\": \"blob\"}"));
    final State expectedState = new State().withState(Jsons.deserialize("{\"json\": \"blob\"}"));

    final State convertedState = StateMessageHelper.getState(stateWrapper);
    Assertions.assertThat(convertedState).isEqualTo(expectedState);
  }

  @Test
  void testGlobalStateConversion() {
    final StateWrapper stateWrapper = new StateWrapper()
        .withStateType(StateType.GLOBAL)
        .withGlobal(
            new AirbyteStateMessage().withType(AirbyteStateType.GLOBAL).withGlobal(
                new AirbyteGlobalState()
                    .withSharedState(Jsons.deserialize("\"shared\""))
                    .withStreamStates(Collections.singletonList(
                        new AirbyteStreamState()
                            .withStreamDescriptor(new StreamDescriptor().withNamespace("ns").withName("name"))
                            .withStreamState(Jsons.deserialize("\"stream state\""))))));
    final State expectedState = new State().withState(Jsons.deserialize(
        """
        [{
          "type":"GLOBAL",
          "global":{
             "shared_state":"shared",
             "stream_states":[
               {"stream_descriptor":{"name":"name","namespace":"ns"},"stream_state":"stream state"}
             ]
          }
        }]
        """));

    final State convertedState = StateMessageHelper.getState(stateWrapper);
    Assertions.assertThat(convertedState).isEqualTo(expectedState);
  }

  @Test
  void testStreamStateConversion() {
    final StateWrapper stateWrapper = new StateWrapper()
        .withStateType(StateType.STREAM)
        .withStateMessages(Arrays.asList(
            new AirbyteStateMessage().withType(AirbyteStateType.STREAM).withStream(
                new AirbyteStreamState()
                    .withStreamDescriptor(new StreamDescriptor().withNamespace("ns1").withName("name1"))
                    .withStreamState(Jsons.deserialize("\"state1\""))),
            new AirbyteStateMessage().withType(AirbyteStateType.STREAM).withStream(
                new AirbyteStreamState()
                    .withStreamDescriptor(new StreamDescriptor().withNamespace("ns2").withName("name2"))
                    .withStreamState(Jsons.deserialize("\"state2\"")))));
    final State expectedState = new State().withState(Jsons.deserialize(
        """
        [
          {"type":"STREAM","stream":{"stream_descriptor":{"name":"name1","namespace":"ns1"},"stream_state":"state1"}},
          {"type":"STREAM","stream":{"stream_descriptor":{"name":"name2","namespace":"ns2"},"stream_state":"state2"}}
        ]
        """));

    final State convertedState = StateMessageHelper.getState(stateWrapper);
    Assertions.assertThat(convertedState).isEqualTo(expectedState);
  }

}
