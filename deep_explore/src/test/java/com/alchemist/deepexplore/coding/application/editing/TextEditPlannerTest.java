package com.alchemist.deepexplore.coding.application.editing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alchemist.deepexplore.coding.application.CodingToolException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextEditPlannerTest {

    private final TextEditPlanner planner = new TextEditPlanner();

    @Test
    void appliesMultipleOperationsAgainstTheSameOriginalSnapshot() {
        TextEditPlan plan = planner.plan(
                "alpha beta gamma",
                List.of(
                        edit("alpha", "one"),
                        edit("gamma", "three")
                ),
                32,
                200_000
        );

        assertThat(plan.content()).isEqualTo("one beta three");
        assertThat(plan.operations()).isEqualTo(2);
        assertThat(plan.replacements()).isEqualTo(2);
    }

    @Test
    void replacesEveryNonOverlappingOccurrenceWhenRequested() {
        TextEditPlan plan = planner.plan(
                "old + old + old",
                List.of(new TextEditOperation("old", "new", true)),
                32,
                200_000
        );

        assertThat(plan.content()).isEqualTo("new + new + new");
        assertThat(plan.replacements()).isEqualTo(3);
    }

    @Test
    void rejectsMissingTargetWithOperationNumber() {
        assertThatThrownBy(() -> planner.plan(
                "current text",
                List.of(edit("stale text", "new text")),
                32,
                200_000
        ))
                .isInstanceOf(CodingToolException.class)
                .satisfies(error -> {
                    CodingToolException toolError =
                            (CodingToolException) error;
                    assertThat(toolError.code())
                            .isEqualTo("EDIT_TARGET_NOT_FOUND");
                    assertThat(toolError.data()).asInstanceOf(
                            org.assertj.core.api.InstanceOfAssertFactories.MAP
                    ).containsEntry("operation", 1);
                });
    }

    @Test
    void rejectsAmbiguousTargetUnlessReplaceAllIsExplicit() {
        assertCode(
                () -> planner.plan(
                        "same same",
                        List.of(edit("same", "other")),
                        32,
                        200_000
                ),
                "EDIT_TARGET_AMBIGUOUS"
        );
    }

    @Test
    void rejectsOverlappingOperations() {
        assertCode(
                () -> planner.plan(
                        "abcdef",
                        List.of(
                                edit("abc", "one"),
                                edit("bcde", "two")
                        ),
                        32,
                        200_000
                ),
                "EDIT_TARGET_OVERLAP"
        );
    }

    @Test
    void matchesNormalizedTextAndPreservesCrLfFileStyle() {
        TextEditPlan plan = planner.plan(
                "one\r\ntwo\r\nthree\r\n",
                List.of(edit("two\nthree", "second\nthird")),
                32,
                200_000
        );

        assertThat(plan.content())
                .isEqualTo("one\r\nsecond\r\nthird\r\n");
    }

    @Test
    void rejectsEmptyNoOpAndOversizedRequests() {
        assertCode(
                () -> planner.plan("text", List.of(), 32, 200_000),
                "EDIT_OPERATIONS_REQUIRED"
        );
        assertCode(
                () -> planner.plan(
                        "text",
                        List.of(edit("", "replacement")),
                        32,
                        200_000
                ),
                "EDIT_TARGET_EMPTY"
        );
        assertCode(
                () -> planner.plan(
                        "text",
                        List.of(edit("text", "text")),
                        32,
                        200_000
                ),
                "EDIT_NO_CHANGE"
        );
        assertCode(
                () -> planner.plan(
                        "text",
                        List.of(edit("text", "replacement")),
                        32,
                        5
                ),
                "EDIT_INPUT_TOO_LARGE"
        );
    }

    private static TextEditOperation edit(String oldText, String newText) {
        return new TextEditOperation(oldText, newText, false);
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String code
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(CodingToolException.class)
                .extracting(error -> ((CodingToolException) error).code())
                .isEqualTo(code);
    }
}
