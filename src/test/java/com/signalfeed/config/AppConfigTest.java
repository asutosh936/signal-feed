package com.signalfeed.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConfigTest {

    private final AppConfig config = new AppConfig();

    @Test
    void chatClient_returnsInstanceFromBuilder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient expected = mock(ChatClient.class);
        when(builder.build()).thenReturn(expected);

        ChatClient actual = config.chatClient(builder);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void chatClient_callsBuildExactlyOnce() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);

        config.chatClient(builder);

        verify(builder).build();
    }
}
