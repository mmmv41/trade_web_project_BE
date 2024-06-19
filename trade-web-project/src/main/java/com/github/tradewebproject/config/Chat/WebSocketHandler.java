package com.github.tradewebproject.config.Chat;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tradewebproject.Dto.Chat.ChatMessageDto;
import com.github.tradewebproject.Dto.Chat.WebSocketMessage;
import com.github.tradewebproject.Dto.User.UserDto;
import com.github.tradewebproject.domain.ChatMessage;
import com.github.tradewebproject.domain.ChatRoom;
import com.github.tradewebproject.domain.User;
import com.github.tradewebproject.repository.Chat.ChatRoomRepository;
import com.github.tradewebproject.repository.User.UserRepository;
import com.github.tradewebproject.service.Chat.ChatMessageService;
import com.github.tradewebproject.service.Jwt.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ChatRoomRepository chatRoomRepository;

    @Autowired
    public WebSocketHandler(ChatMessageService chatMessageService, UserRepository userRepository, JwtService jwtService, ChatRoomRepository chatRoomRepository, ObjectMapper objectMapper) {
        this.chatMessageService = chatMessageService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.chatRoomRepository = chatRoomRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = session.getHandshakeHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            User user = getUserFromToken(token);
            if (user != null) {
                Long chatRoomId = getChatRoomIdFromSession(session); // 채팅방 ID를 세션에서 가져오는 메서드
                if (isUserAuthorizedForChatRoom(user, chatRoomId)) { // 권한 확인 로직
                    sessions.put(user.getUserId(), session);
                    session.getAttributes().put("chatRoomId", chatRoomId); // chatRoomId를 세션 속성에 저장
                } else {
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                }
            } else {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            }
        } else {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketMessage webSocketMessageDto = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

        String token = getTokenFromSession(session);
        User user = getUserFromToken(token);
        if (user != null) {
            Long chatRoomId = (Long) session.getAttributes().get("chatRoomId");
            if (chatRoomId != null && isUserAuthorizedForChatRoom(user, chatRoomId)) {
                Long senderId = user.getUserId();
                ChatMessageDto chatMessageDto = null;
                switch (webSocketMessageDto.getMessageType()) {
                    case "TEXT":
                        chatMessageDto = chatMessageService.sendTextMessage(
                                chatRoomId,
                                senderId,
                                webSocketMessageDto.getMessageContent()
                        );
                        break;
                    case "IMAGE":
                        String imageUrl = webSocketMessageDto.getImageUrl().replace("\\", "/");
                        chatMessageDto = chatMessageService.sendImageMessage(
                                chatRoomId,
                                senderId,
                                imageUrl
                        );
                        break;
                    case "EMOJI":
                        chatMessageDto = chatMessageService.sendEmojiMessage(
                                chatRoomId,
                                senderId,
                                webSocketMessageDto.getEmojiCode()
                        );
                        break;
                    default:
                        session.close(CloseStatus.NOT_ACCEPTABLE);
                        return;
                }

                sendNotification(user, chatMessageDto);

                TextMessage responseMessage = new TextMessage(objectMapper.writeValueAsString(chatMessageDto));
                for (WebSocketSession webSocketSession : sessions.values()) {
                    if (webSocketSession.isOpen()) {
                        webSocketSession.sendMessage(responseMessage);
                    }
                }
            } else {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            }
        } else {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        WebSocketMessage webSocketMessageDto = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);
//
//        String token = getTokenFromSession(session);
//        User user = getUserFromToken(token);
//        if (user != null) {
//            Long chatRoomId = (Long) session.getAttributes().get("chatRoomId"); // 세션 속성에서 chatRoomId 가져오기
//            if (chatRoomId != null && isUserAuthorizedForChatRoom(user, chatRoomId)) {
//                Long senderId = user.getUserId();
//                ChatMessageDto chatMessageDto = chatMessageService.sendMessage(
//                        chatRoomId,
//                        senderId,
//                        webSocketMessageDto.getMessageContent()
//                );
//
//                TextMessage responseMessage = new TextMessage(objectMapper.writeValueAsString(chatMessageDto));
//                for (WebSocketSession webSocketSession : sessions.values()) {
//                    if (webSocketSession.isOpen()) {
//                        webSocketSession.sendMessage(responseMessage);
//                    }
//                }
//            } else {
//                session.close(CloseStatus.NOT_ACCEPTABLE);
//            }
//        } else {
//            session.close(CloseStatus.NOT_ACCEPTABLE);
//        }
//    }

    private Long getChatRoomIdFromSession(WebSocketSession session) {
        // WebSocketSession의 URL에서 채팅방 ID를 추출하는 방법
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("chatRoomId=")) {
            return Long.valueOf(query.split("=")[1]);
        }
        return null;
    }

    private boolean isUserAuthorizedForChatRoom(User user, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
        if (chatRoom == null) {
            return false;
        }
        return chatRoom.getSeller().getUserId().equals(user.getUserId()) ||
                chatRoom.getBuyer().getUserId().equals(user.getUserId());
    }

    private String getTokenFromSession(WebSocketSession session) {
        // WebSocketSession의 헤더에서 토큰을 추출하는 방법
        List<String> authorization = session.getHandshakeHeaders().get("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            return authorization.get(0).replace("Bearer ", "");
        }
        return null;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = null;
        for (Map.Entry<Long, WebSocketSession> entry : sessions.entrySet()) {
            if (entry.getValue().equals(session)) {
                userId = entry.getKey();
                break;
            }
        }
        if (userId != null) {
            sessions.remove(userId);
        }
    }

    private User getUserFromToken(String token) {
        try {
            // 토큰을 검증하고 사용자 정보를 가져오는 로직
            UserDto userDto = jwtService.checkAccessTokenValid(token);
            if (userDto != null) {
                return userRepository.findByEmail2(userDto.getEmail()).orElse(null);
            }
            return null;
        } catch (Exception e) {
            log.error("Error while retrieving user from token", e);
            return null;
        }
    }

    private void sendNotification(User user, ChatMessageDto chatMessageDto) {
        // 알림 전송 로직 구현 (예: FCM, APNS, 이메일 등)
        log.info("Send notification to user: " + user.getUserId() + " - Message: " + chatMessageDto.getContent());
    }
}