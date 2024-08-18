package com.iot.drawinggameapp.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.iot.drawinggameapp.CustomApplication;
import com.iot.drawinggameapp.Header;
import com.iot.drawinggameapp.R;
import com.iot.drawinggameapp.SocketService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class WaitActivity extends AppCompatActivity {
    private CustomApplication application;
    private ExecutorService executors;
    private Handler handler;

    private Button button_play;
    private Button button_send;
    private TextView topText;
    private EditText editText;
    private TextView[] userList = new TextView[8];

    // 채팅
    private ListView listView_chat;
    private List<String> chats = new ArrayList<>();
    private ArrayAdapter<String> chat_adapter;

    private Header currentHeader = null;
    private List<String> data = new ArrayList<>();
    private int dataCount;
    private int itemCount;

    private SocketService socketService;
    private boolean isBound = false;
    private BroadcastReceiver receiver;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            SocketService.SocketBinder binder = (SocketService.SocketBinder) iBinder;
            socketService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wait);

        application = CustomApplication.getInstance();
        executors = application.getExecutors();
        handler = new Handler(Looper.getMainLooper());

        button_play = findViewById(R.id.button_play);
        button_send = findViewById(R.id.button_send);
        topText = findViewById(R.id.text_room);
        editText = findViewById(R.id.editText);

        userList[0] = findViewById(R.id.text_user1);
        userList[1] = findViewById(R.id.text_user2);
        userList[2] = findViewById(R.id.text_user3);
        userList[3] = findViewById(R.id.text_user4);
        userList[4] = findViewById(R.id.text_user5);
        userList[5] = findViewById(R.id.text_user6);
        userList[6] = findViewById(R.id.text_user7);
        userList[7] = findViewById(R.id.text_user8);

        // 채팅
        listView_chat = findViewById(R.id.listView_Chat);
        chat_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chats);
        listView_chat.setAdapter(chat_adapter);

        if(application.getIsHost()) {
            button_play.setVisibility(View.VISIBLE);
        }

        setReceiver();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = new Intent(this, SocketService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter("socket");
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        setPlayButton();
        setSendButton();
    }

    @Override
    protected void onStop() {

        button_send.setOnClickListener(null);
        button_play.setOnClickListener(null);

        if(isBound) {
            unbindService(connection);
        }
        unregisterReceiver(receiver);

        super.onStop();
    }

    public void setReceiver() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String input = intent.getStringExtra("data");

                if(currentHeader == null) {
                    // 초기화
                    data.clear();
                    dataCount = 0;
                    itemCount = -1;
                    currentHeader = Header.getType(input);

                    // 헤더에 따라 값 세팅
                    if(currentHeader == Header.REFRESH_ROOM || currentHeader == Header.CHAT || currentHeader == Header.PLAY_GAME) {
                        dataCount = 1;
                    }  else {
                        processMSG();
                    }
                } else {
                    // 받아온 유저의 수 세팅
                    if (currentHeader == Header.REFRESH_ROOM && itemCount == -1) {
                        itemCount = Integer.parseInt(input);
                        dataCount = itemCount + 1;  // 유저들 + 방 이름
                    } else {
                        // 데이터 추가 후 개수 확인
                        data.add(input);

                        if (dataCount == data.size()) {
                            processMSG();
                        }
                    }
                }
            }
        };
    }

    private void processMSG() {
        Log.i("wait", currentHeader.name() + " " + data);

        executors.submit(()->{
            String roomName;

            switch (currentHeader) {
                case ERROR:
                    break;
                case HEARTBEAT:
                    socketService.connectCheck();
                    socketService.sendMessage(Header.REFRESH_ROOM, null);
                    break;
                case CHAT:
                    chats.add(data.get(0));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            chat_adapter.notifyDataSetChanged();
                        }
                    });
                    break;
                case REFRESH_ROOM:
                    roomName = data.get(0);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            topText.setText(roomName);
                            for(int i = 0; i < itemCount; i++) {
                                Log.i("Wait", "유저" + i + " set");
                                userList[i].setText(data.get(i + 1));
                            }
                        }
                    }); break;
                case PLAY_GAME:
                    if(data.get(0).equals("t")) {
                        nextActivity(DrawActivity.class);
                    } else  {
                        nextActivity(PlayActivity.class);
                    }
            }

            currentHeader = null;
        });
    }

    private void setPlayButton() {
        button_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        socketService.sendMessage(Header.PLAY_GAME, null);
                    }
                });
                thread.start();
            }
        });
    }

    private void setSendButton() {
        button_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String chat = editText.getText().toString();
                editText.setText("");
                executors.submit(()->{
                    socketService.sendMessage(Header.CHAT, chat + "\n");
                });
            }
        });
    }

    private void nextActivity(Class activity) {
        Intent intent = new Intent(this, activity);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        executors.submit(()->{
            socketService.sendMessage(Header.LEAVE_ROOM, null);
        });
        super.onBackPressed();
    }
}