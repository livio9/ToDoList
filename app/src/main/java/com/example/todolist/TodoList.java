package com.example.todolist;

import android.app.Application;

import com.parse.Parse;



public class TodoList extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Parse.initialize(new Parse.Configuration.Builder(this)
                .applicationId("myTODOListId")
                .server("http://121.43.161.183:1337/parse")
                .build()
        );
    }
}