package se.gcom;

import javafx.application.Application;

public class Main {
    public static void main(String[] args) {
        // Delegate startup to the JavaFX Application subclass
        Application.launch(App.class, args);
    }
}