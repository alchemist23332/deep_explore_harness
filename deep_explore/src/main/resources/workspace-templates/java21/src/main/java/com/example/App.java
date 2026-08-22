package com.example;

public final class App {

    private App() {
    }

    public static String greeting() {
        return "Hello from Java 21";
    }

    public static void main(String[] args) {
        System.out.println(greeting());
    }
}
