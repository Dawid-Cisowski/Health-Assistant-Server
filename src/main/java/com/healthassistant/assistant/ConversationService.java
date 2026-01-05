package com.healthassistant.assistant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    @Transactional
    public UUID getOrCreateConversation(UUID conversationId, String deviceId) {
        if (conversationId == null) {
            var conversation = new Conversation(deviceId);
            conversationRepository.save(conversation);
            log.info("Created new conversation {} for device {}", conversation.getId(), deviceId);
            return conversation.getId();
        }

        var conversation = conversationRepository.findByIdAndDeviceId(conversationId, deviceId)
                .orElseThrow(() -> {
                    log.warn("SECURITY_EVENT: Unauthorized conversation access. conversationId={}, deviceId={}", conversationId, deviceId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
                });

        log.info("Using existing conversation {} for device {}", conversationId, deviceId);
        return conversation.getId();
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> loadConversationHistory(UUID conversationId) {
        var messages = messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(messages);
        log.info("Loaded {} messages from conversation {}", messages.size(), conversationId);
        return messages;
    }

    @Transactional
    public void saveMessage(UUID conversationId, MessageRole role, String content) {
        var message = new ConversationMessage(conversationId, role, content);
        messageRepository.save(message);

        conversationRepository.findById(conversationId).ifPresent(Conversation::touch);

        log.info("Saved {} message to conversation {}", role, conversationId);
    }

    public List<Message> buildMessageList(List<ConversationMessage> history, String systemPrompt, String currentUserMessage) {
        var result = Stream.concat(
                Stream.concat(
                        Stream.of((Message) new SystemMessage(systemPrompt)),
                        history.stream().map(this::toSpringAiMessage)
                ),
                Stream.of((Message) new UserMessage(currentUserMessage))
        ).toList();

        log.info("Built message list: 1 system + {} history + 1 current = {} total", history.size(), result.size());
        return result;
    }

    private Message toSpringAiMessage(ConversationMessage msg) {
        return switch (msg.getRole()) {
            case USER -> new UserMessage(msg.getContent());
            case ASSISTANT -> new AssistantMessage(msg.getContent());
        };
    }

    @Transactional
    public void deleteAllConversations() {
        log.warn("Deleting all conversations and messages");
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
    }
}
