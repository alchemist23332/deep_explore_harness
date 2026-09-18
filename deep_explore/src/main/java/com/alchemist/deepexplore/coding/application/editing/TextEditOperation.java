package com.alchemist.deepexplore.coding.application.editing;

public record TextEditOperation(
        String oldText,
        String newText,
        boolean replaceAll
) {
}
