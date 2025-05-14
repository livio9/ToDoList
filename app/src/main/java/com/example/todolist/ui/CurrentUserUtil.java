package com.example.todolist.ui;

import com.parse.ParseUser;

public class CurrentUserUtil {
    public static String getCurrentUserId() {
        ParseUser currentUser = ParseUser.getCurrentUser();
        if (currentUser != null) {
            return currentUser.getObjectId();
        }
        return null;
    }
}
