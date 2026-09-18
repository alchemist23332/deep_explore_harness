package com.alchemist.deepexplore.coding.application.editing;

import com.alchemist.deepexplore.coding.application.CodingToolException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TextEditPlanner {

    public TextEditPlan plan(
            String original,
            List<TextEditOperation> operations,
            int maxOperations,
            int maxInputCharacters
    ) {
        if (operations == null || operations.isEmpty()) {
            throw error(
                    "EDIT_OPERATIONS_REQUIRED",
                    "At least one edit operation is required",
                    Map.of()
            );
        }
        if (operations.size() > maxOperations) {
            throw error(
                    "TOO_MANY_EDIT_OPERATIONS",
                    "Edit operation count exceeds the configured limit",
                    Map.of(
                            "actual", operations.size(),
                            "maximum", maxOperations
                    )
            );
        }

        long inputCharacters = 0;
        for (TextEditOperation operation : operations) {
            if (operation != null) {
                inputCharacters += length(operation.oldText());
                inputCharacters += length(operation.newText());
            }
        }
        if (inputCharacters > maxInputCharacters) {
            throw error(
                    "EDIT_INPUT_TOO_LARGE",
                    "Edit input exceeds the configured character limit",
                    Map.of(
                            "actual", inputCharacters,
                            "maximum", maxInputCharacters
                    )
            );
        }

        String source = normalizeLineEndings(original == null ? "" : original);
        List<Replacement> replacements = new ArrayList<>();
        for (int index = 0; index < operations.size(); index++) {
            TextEditOperation operation = operations.get(index);
            int operationNumber = index + 1;
            validateOperation(operation, operationNumber);
            String target = normalizeLineEndings(operation.oldText());
            String replacement = normalizeLineEndings(operation.newText());
            if (target.equals(replacement)) {
                throw error(
                        "EDIT_NO_CHANGE",
                        "Edit operation does not change the file",
                        Map.of("operation", operationNumber)
                );
            }

            List<Integer> matches = findMatches(source, target);
            if (matches.isEmpty()) {
                throw error(
                        "EDIT_TARGET_NOT_FOUND",
                        "Edit target was not found in the current file",
                        Map.of("operation", operationNumber)
                );
            }
            if (!operation.replaceAll() && matches.size() > 1) {
                throw error(
                        "EDIT_TARGET_AMBIGUOUS",
                        "Edit target occurs more than once; include more context or set replaceAll",
                        Map.of(
                                "operation", operationNumber,
                                "matches", matches.size()
                        )
                );
            }
            List<Integer> selected = operation.replaceAll()
                    ? matches
                    : List.of(matches.getFirst());
            for (int offset : selected) {
                replacements.add(new Replacement(
                        operationNumber,
                        offset,
                        offset + target.length(),
                        replacement
                ));
            }
        }

        replacements.sort(Comparator.comparingInt(Replacement::start));
        for (int index = 1; index < replacements.size(); index++) {
            Replacement previous = replacements.get(index - 1);
            Replacement current = replacements.get(index);
            if (current.start() < previous.end()) {
                throw error(
                        "EDIT_TARGET_OVERLAP",
                        "Edit operations target overlapping text",
                        Map.of(
                                "firstOperation", previous.operation(),
                                "secondOperation", current.operation()
                        )
                );
            }
        }

        StringBuilder edited = new StringBuilder(source);
        for (int index = replacements.size() - 1; index >= 0; index--) {
            Replacement replacement = replacements.get(index);
            edited.replace(
                    replacement.start(),
                    replacement.end(),
                    replacement.newText()
            );
        }
        String content = restoreLineEndings(
                edited.toString(),
                usesCrLf(original)
        );
        return new TextEditPlan(
                content,
                operations.size(),
                replacements.size(),
                lineCount(original),
                lineCount(content)
        );
    }

    private static void validateOperation(
            TextEditOperation operation,
            int operationNumber
    ) {
        if (operation == null
                || operation.oldText() == null
                || operation.oldText().isEmpty()) {
            throw error(
                    "EDIT_TARGET_EMPTY",
                    "oldText must not be empty",
                    Map.of("operation", operationNumber)
            );
        }
        if (operation.newText() == null) {
            throw error(
                    "EDIT_REPLACEMENT_REQUIRED",
                    "newText is required; use an empty string to delete text",
                    Map.of("operation", operationNumber)
            );
        }
    }

    private static List<Integer> findMatches(String source, String target) {
        List<Integer> matches = new ArrayList<>();
        int from = 0;
        while (from <= source.length() - target.length()) {
            int offset = source.indexOf(target, from);
            if (offset < 0) {
                break;
            }
            matches.add(offset);
            // Advance by one so overlapping occurrences are still detected.
            // replaceAll will reject them later as overlapping edits.
            from = offset + 1;
        }
        return matches;
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String restoreLineEndings(String value, boolean crLf) {
        return crLf ? value.replace("\n", "\r\n") : value;
    }

    private static boolean usesCrLf(String value) {
        return value != null && value.contains("\r\n");
    }

    private static int lineCount(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return (int) normalizeLineEndings(value).chars()
                .filter(character -> character == '\n')
                .count() + 1;
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static CodingToolException error(
            String code,
            String message,
            Map<String, Object> data
    ) {
        return new CodingToolException(code, message, data);
    }

    private record Replacement(
            int operation,
            int start,
            int end,
            String newText
    ) {
    }
}
