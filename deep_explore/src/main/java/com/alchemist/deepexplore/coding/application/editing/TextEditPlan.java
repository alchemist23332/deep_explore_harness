package com.alchemist.deepexplore.coding.application.editing;

public record TextEditPlan(
        String content,
        int operations,
        int replacements,
        int beforeLines,
        int afterLines
) {
}
