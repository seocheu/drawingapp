package com.iot.drawinggameapp;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import static com.iot.drawinggameapp.CustomApplication.ADDR;
import static com.iot.drawinggameapp.CustomApplication.PORT;
import static com.iot.drawinggameapp.CustomApplication.TIME_OUT;
import static com.iot.drawinggameapp.CustomApplication.executors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class SocketService extends Service {

    private Socket socket;
    private BufferedReader bufferedReader;
    private OutputStream outputStream;
    private CustomApplication application;
    private IBinder binder = new SocketBinder();
    private static final Object lock = new Object();

    private StringBuilder stringBuilder = new StringBuilder();
    private boolean isDisconnected;
    private long lastReceivedTime;

    public class SocketBinder extends Binder {
        public SocketService getService() {
            return SocketService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        application = CustomApplication.getInstance();

        isDisconnected = false;
        connectServer();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void initStream() throws IOException{
        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        outputStream = socket.getOutputStream();
    }

    public void connectServer() {
        executors.submit(()-> {
            while(true) {
                try {
                    socket = new Socket(ADDR, PORT);
                    socket.setSoLinger(true, 0);
                    socket.setKeepAlive(true);
                    initStream();
                    Log.i("Socket", "연결");
                    isDisconnected = false;
                    sendUserInfo();

                     lastReceivedTime = System.currentTimeMillis();
                    while (!isDisconnected) {
                        if (Long.sum(System.currentTimeMillis(), -lastReceivedTime) > TIME_OUT) {
                            Log.i("Disconnect", "Time Out");
                            isDisconnected = true;
                        }

                        String input;
                        while ((input = bufferedReader.readLine()) != null) {
                            Log.i("test", input);
                            Intent intent = new Intent("socket");
                            intent.putExtra("data", input);
                            sendBroadcast(intent);
                        }
                    }
                } catch (IOException e) {
                    Log.i("Socket", "소켓 연결 실패");
                } finally {
                    socket.close();
                }
            }
        });
    }

    public void socketDisconnected() {
        Log.i("Disconnect", "메소드 호출됨");
        this.isDisconnected = true;
    }

    public void sendMessage(Header header, String message) {
        Log.i("Send", "o " + header.name());
        try {
            synchronized (lock) {
                outputStream.write(header.getValue().getBytes());
                outputStream.write("\n".getBytes());
                if (message != null) {
                    outputStream.write(message.getBytes());
                }
                outputStream.flush();
            }
        } catch (IOException e) {
            isDisconnected = true;
            Log.i("Disconnect", "Output Stream 오류");
        }
    }

    public synchronized void connectCheck() {
        lastReceivedTime = System.currentTimeMillis();
        sendMessage(Header.HEARTBEAT, null);
    }

    public void sendUserInfo() {
        stringBuilder.setLength(0);
        sendMessage(Header.SET_CLIENT_INFO, stringBuilder.append(application.getUserID()).append(",")
                .append(application.getUserName()).append(",")
                .append(application.getRoomID()).append("\n").toString());
    }
}
