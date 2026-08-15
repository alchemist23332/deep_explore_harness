package com.alchemist.deepexplore.conversation.adapter.in.web;

import com.alchemist.deepexplore.conversation.application.ConversationNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConversationController.class)
public class ConversationExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(ConversationNotFoundException error) {
        return Map.of("message", error.getMessage());
    }
}
