package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.harness.application.execution.RunNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RunController.class)
public class RunExceptionHandler {

    @ExceptionHandler(RunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(RunNotFoundException error) {
        return Map.of(
                "code",
                "RUN_NOT_FOUND",
                "message",
                error.getMessage()
        );
    }
}
