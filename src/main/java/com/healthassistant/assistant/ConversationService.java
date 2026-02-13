package com.healthassistant.assistant;

import com.healthassistant.assistant.api.dto.ConversationDetailResponse;
import com.healthassistant.assistant.api.dto.ConversationMessageResponse;
import com.healthassistant.assistant.api.dto.ConversationSummaryResponse;
import com.healthassistant.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
            log.info("Created new conversation {} for device {}", conversation.getId(), SecurityUtils.maskDeviceId(deviceId));
            return conversation.getId();
        }

        var conversation = conversationRepository.findByIdAndDeviceId(conversationId, deviceId)
                .orElseThrow(() -> {
                    log.warn("SECURITY_EVENT: Unauthorized conversation access. conversationId={}, deviceId={}", conversationId, SecurityUtils.maskDeviceId(deviceId));
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
                });

        log.info("Using existing conversation {} for device {}", conversationId, SecurityUtils.maskDeviceId(deviceId));
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

    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> listConversations(String deviceId, Pageable pageable) {
        var conversationsPage = conversationRepository.findByDeviceIdOrderByUpdatedAtDesc(deviceId, pageable);

        if (conversationsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        var conversationIds = conversationsPage.getContent().stream()
                .map(Conversation::getId)
                .toList();

        var firstMessages = messageRepository.findFirstUserMessagePerConversation(conversationIds.toArray(UUID[]::new));
        Map<UUID, String> previewMap = firstMessages.stream()
                .collect(Collectors.toMap(ConversationMessage::getConversationId, ConversationMessage::getContent));

        var countResults = messageRepository.countByConversationIds(conversationIds);
        Map<UUID, Integer> countMap = countResults.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).intValue()
                ));

        return conversationsPage.map(conversation -> new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                truncatePreview(previewMap.get(conversation.getId())),
                countMap.getOrDefault(conversation.getId(), 0)
        ));
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDetailResponse> getConversationDetail(UUID conversationId, String deviceId) {
        return conversationRepository.findByIdAndDeviceId(conversationId, deviceId)
                .map(conversation -> {
                    var messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
                    var messageResponses = messages.stream()
                            .map(msg -> new ConversationMessageResponse(
                                    msg.getRole().name(),
                                    msg.getContent(),
                                    msg.getCreatedAt()
                            ))
                            .toList();
                    return new ConversationDetailResponse(
                            conversation.getId(),
                            conversation.getCreatedAt(),
                            conversation.getUpdatedAt(),
                            messageResponses
                    );
                });
    }

    private static String truncatePreview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }

    @Transactional
    public void deleteAllConversations() {
        log.warn("Deleting all conversations and messages");
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Transactional
    public void deleteConversationsByDeviceId(String deviceId) {
        log.warn("Deleting conversations for device: {}", SecurityUtils.maskDeviceId(deviceId));
        conversationRepository.deleteByDeviceId(deviceId);
    }
}
