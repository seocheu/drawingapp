package com.iot.drawinggameapp;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.util.Log;
import android.widget.TextView;

import com.iot.drawinggameapp.activity.DrawActivity;
import com.iot.drawinggameapp.activity.MainActivity;
import com.iot.drawinggameapp.activity.PlayActivity;
import com.iot.drawinggameapp.activity.WaitActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CustomApplication extends Application {

    static final String ADDR = "10.0.2.2";
    static final int PORT = 9999;
    public static final int BUFFER_SIZE = 4096;
    private static final String PREF_NAME = "User";
    private static final String KEY_USERID = "UserID";
    private static final String KEY_USERNAME = "UserName";
    private static CustomApplication instance;

    static final ExecutorService executors = Executors.newFixedThreadPool(8);
    private StringBuilder stringBuilder = new StringBuilder();
    private SharedPreferences preference;

    private String userID;
    private String userName;
    private int roomID;
    private boolean isHost;

    static final int TIME_OUT = 30000;   // 30초

    // getter, setter
    public static CustomApplication getInstance() {
        return instance;
    }

    public ExecutorService getExecutors() {
        return executors;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserID() { return userID; }

    public int getRoomID() { return roomID; }

    public void setUserName(String userName) {
        this.userName = userName;
        putPreferenceString(KEY_USERNAME, userName);
    }

    public void setUserID(String userID) {
        this.userID = userID;
        putPreferenceString(KEY_USERID, userID);
    }

    public void setRoomID(int roomID) {
        this.roomID = roomID;
    }

    public void setIsHost(boolean b) { this.isHost = b; }

    public boolean getIsHost() { return isHost; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        preference = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        userID = getPreference(KEY_USERID);
        userName = getPreference(KEY_USERNAME);
        roomID = 0;
    }

    private String getPreference(String key) {
        return preference.getString(key, "user");
    }

    private void putPreferenceString(String key, String value) {
        SharedPreferences.Editor editor = preference.edit();
        editor.putString(key, value);
        editor.commit();
    }
}
