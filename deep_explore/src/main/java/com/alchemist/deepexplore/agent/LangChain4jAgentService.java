package com.alchemist.deepexplore.agent;

import java.util.ArrayList;
import java.util.List;

import com.alchemist.deepexplore.config.AiModelProperties;
import com.alchemist.deepexplore.conversation.ConversationMessage;
import com.alchemist.deepexplore.conversation.ConversationStore;
import com.alchemist.deepexplore.conversation.ConversationTurn;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class LangChain4jAgentService implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jAgentService.class);

    private final StreamingChatModel fastChatModel;
    private final StreamingChatModel deepChatModel;
    private final AiModelProperties modelProperties;
    private final ConversationStore conversationStore;
    private final String systemPrompt;

    public LangChain4jAgentService(
            @Qualifier("fastStreamingChatModel") StreamingChatModel fastChatModel,
            @Qualifier("deepStreamingChatModel") StreamingChatModel deepChatModel,
            AiModelProperties modelProperties,
            ConversationStore conversationStore,
            @Value("${ai.agent.system-prompt}") String systemPrompt
    ) {
        this.fastChatModel = fastChatModel;
        this.deepChatModel = deepChatModel;
        this.modelProperties = modelProperties;
        this.conversationStore = conversationStore;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public Flux<AgentEvent> stream(AgentCommand command) {
        ConversationTurn turn;
        try {
            turn = conversationStore.beginTurn(command.conversationId(), command.message());
        } catch (IllegalStateException exception) {
            return Flux.just(AgentEvent.error(command.conversationId(), exception.getMessage()));
        }

        if (!modelProperties.isConfigured()) {
            conversationStore.failTurn(turn.conversationId());
            return Flux.just(
                    AgentEvent.metadata(turn.conversationId()),
                    AgentEvent.error(turn.conversationId(), "服务端尚未配置 AI_API_KEY")
            );
        }

        return Flux.create(sink -> {
            sink.next(AgentEvent.metadata(turn.conversationId()));
            StringBuilder answer = new StringBuilder();

            modelFor(command.mode()).chat(toChatMessages(turn), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    answer.append(partialResponse);
                    sink.next(AgentEvent.delta(turn.conversationId(), partialResponse));
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    String completeAnswer = answer.isEmpty()
                            ? completeResponse.aiMessage().text()
                            : answer.toString();
                    conversationStore.completeTurn(turn.conversationId(), completeAnswer);
                    sink.next(AgentEvent.done(turn.conversationId()));
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Model call failed for conversation {}", turn.conversationId(), error);
                    conversationStore.failTurn(turn.conversationId());
                    sink.next(AgentEvent.error(
                            turn.conversationId(),
                            "模型调用失败，请检查模型地址、名称和 API Key"
                    ));
                    sink.complete();
                }
            });
        });
    }

    private StreamingChatModel modelFor(AgentMode mode) {
        return mode == AgentMode.DEEP ? deepChatModel : fastChatModel;
    }

    private List<ChatMessage> toChatMessages(ConversationTurn turn) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));

        for (ConversationMessage message : turn.messages()) {
            if (message.role() == ConversationMessage.Role.USER) {
                messages.add(UserMessage.from(message.content()));
            } else {
                messages.add(AiMessage.from(message.content()));
            }
        }
        return messages;
    }
}
