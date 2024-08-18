package com.iot.drawinggameapp.activity;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;
import com.iot.drawinggameapp.CustomApplication;
import com.iot.drawinggameapp.Header;
import com.iot.drawinggameapp.R;
import com.iot.drawinggameapp.SocketService;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class MainActivity extends AppCompatActivity {
    private CustomApplication application;
    private StringBuilder stringBuilder = new StringBuilder();
    private ExecutorService executors;
    private Handler handler;
    private BufferedReader bufferedReader;

    // UI
    private Button button_join;
    private Button button_refresh;
    private ListView listView;
    private Slider slider;
    private TextView sliderText;
    private EditText editText;
    private RadioGroup radioGroup_time;
    private RadioGroup radioGroup_question;
    private Button button_create;
    private Dialog dialog;
    private EditText dialogEditText;
    private Button dialogButton;
    private ImageView loadingImage;

    // 리스트뷰
    private List<String> rooms = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private View prevListItem;

    // Pause시 자원 해제용
    private Slider.OnChangeListener sliderListener;
    private boolean isPause;

    private Header currentHeader = null;
    private List<String> data = new ArrayList<>();
    private int dataCount;
    private int itemCount;

    private int choiceRoom;
    private String roomName;
    private int endTime;
    private int maxUser;
    private int question;

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
        setContentView(R.layout.activity_main);

        maxUser = 8;
        endTime = -1;
        question = -1;

        application = CustomApplication.getInstance();
        executors = application.getExecutors();
        handler = new Handler(Looper.getMainLooper());

        button_join = findViewById(R.id.button_join);
        button_refresh = findViewById(R.id.button_refresh);
        listView = findViewById(R.id.listView);
        slider = findViewById(R.id.slider);
        sliderText = findViewById(R.id.sliderText);
        editText = findViewById(R.id.editText_room);
        radioGroup_time = findViewById(R.id.radioGroup_time);
        radioGroup_question = findViewById(R.id.radioGroup_question);
        button_create = findViewById(R.id.button_create);
        loadingImage = findViewById(R.id.loadingImage);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rooms);
        listView.setAdapter(adapter);

        sliderListener = new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                sliderText.setText(String.valueOf((int)value));
                maxUser = (int)value;
            }
        };

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

        isPause = false;
        choiceRoom = -1;

        showDialog();

        slider.addOnChangeListener(sliderListener);
        setTimeChecked();
        setQuestionChecked();

        setJoinButton();
        setRefreshButton();
        setCreateButton();
        setListViewTouch();

        executors.submit(new Runnable() {
            @Override
            public void run() {
                socketService.sendMessage(Header.REFRESH_MAIN, null);
            }
        });
    }

    @Override
    protected void onStop() {
        isPause = true;
        button_join.setOnClickListener(null);
        button_refresh.setOnClickListener(null);
        button_create.setOnClickListener(null);

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
                    if (currentHeader == Header.REFRESH_MAIN || currentHeader == Header.JOIN_ROOM
                        || currentHeader == Header.REFRESH_ROOM) {
                        dataCount = 1;
                    } else if(currentHeader == Header.CREATE_ROOM) {
                        dataCount = 1;
                        Log.i("test", "create");
                    } else if (currentHeader == Header.SET_CLIENT_INFO) {
                        dataCount = 2;
                    } else {
                        processMSG();
                    }
                } else {
                    // 받아온 방의 개수 세팅
                    if (currentHeader == Header.REFRESH_MAIN && itemCount == -1) {
                        itemCount = Integer.parseInt(input);
                        if(itemCount == 0) {
                            processMSG();
                        } else {
                            dataCount = itemCount;
                        }
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

    public void processMSG() {
        Log.i("main", currentHeader.name() + " " + data);

        executors.submit(()->{

            String id;
            String name;

            switch (currentHeader) {
                case ERROR:
                    break;
                case HEARTBEAT:
                    socketService.connectCheck();
                    if (rooms.isEmpty()) {
                        socketService.sendMessage(Header.REFRESH_MAIN, null);
                    }
                    break;
                case REQUEST_CLIENT_INFO:
                    socketService.sendUserInfo();
                    break;
                case SET_CLIENT_INFO:
                    id = data.get(0);
                    name = data.get(1);
                    application.setUserID(id);
                    application.setUserName(name);
                    break;
                case REFRESH_MAIN:
                    rooms.clear();
                    for (int i = 0; i < itemCount; i++) {
                        rooms.add(data.get(i));
                    }
                    initListView();
                    break;
                case CREATE_ROOM:
                    application.setRoomID(Integer.parseInt(data.get(0)));
                    application.setIsHost(true);
                    nextActivity();
                    break;
                case JOIN_ROOM:
                    application.setRoomID(Integer.parseInt(data.get(0)));
                    application.setIsHost(false);
                    nextActivity();
                    break;
            }

            currentHeader = null;
        });
    }

    private void showDialog() {
        String userName = application.getUserName();
        if(userName.length() < 2 || userName.equals("user")) {
            dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_name);
            dialog.show();

            dialogEditText = dialog.findViewById(R.id.editText);
            dialogButton = dialog.findViewById(R.id.button);

            dialogButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    executors.submit(()->{
                        application.setUserName(dialogEditText.getText().toString());
                        socketService.sendUserInfo();
                        dialog.dismiss();
                    });
                }
            });
        }
    }

    private void setTimeChecked() {
        radioGroup_time.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if(i == R.id.radio_time1) {
                    endTime = 30000;
                } else if (i == R.id.radio_time2) {
                    endTime = 60000;
                }
            }
        });
    }

    private  void setQuestionChecked() {
        radioGroup_question.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if(i == R.id.radio_question1) {
                    question = 1;
                } else if (i == R.id.radio_question2) {
                    question = 2;
                } else if (i == R.id.radio_question3) {
                    question = 3;
                }
            }
        });
    }

    private void setJoinButton() {
        button_join.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                executors.submit(()->{
                    if(choiceRoom != -1) {
                        socketService.sendMessage(Header.JOIN_ROOM, choiceRoom + "\n");
                    }
                });
            }
        });
    }

    private void setRefreshButton() {
        button_refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                executors.submit(()->{
                    socketService.sendMessage(Header.REFRESH_MAIN, null);
                });
            }
        });
    }

    private void setCreateButton() {
        button_create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                roomName = editText.getText().toString();
                if(roomName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "방 이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
                }
                else if(endTime == -1) {
                    Toast.makeText(MainActivity.this, "시간을 선택해주세요.", Toast.LENGTH_SHORT).show();
                }
                else if(question == -1) {
                    Toast.makeText(MainActivity.this, "문제의 수를 선택해주세요.", Toast.LENGTH_SHORT).show();
                }
                else {
                    executors.submit(() -> {
                        stringBuilder.setLength(0);
                        stringBuilder.append(roomName).append("\n");
                        stringBuilder.append(endTime).append("\n");
                        stringBuilder.append(maxUser).append("\n");
                        stringBuilder.append(question).append("\n");

                        socketService.sendMessage(Header.CREATE_ROOM, stringBuilder.toString());

                        try {
                            Thread.sleep(1000);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isPause) {
                                        Toast.makeText(MainActivity.this, "룸 생성에 실패했습니다.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        });
    }

    private void setListViewTouch() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(prevListItem != null) {
                    prevListItem.setBackgroundColor(Color.WHITE);
                }
                view.setBackgroundColor(Color.argb(255, 200, 200, 255));
                prevListItem = view;
                String[] data = ((String)adapterView.getItemAtPosition(i)).split("\\.");
                choiceRoom = Integer.parseInt(data[0]);
                Log.i("test", choiceRoom + "");
            }
        });
    }

    public void initListView() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                loadingImage.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
            }
        });
    }

    public void nextActivity() {
        Intent intent = new Intent(MainActivity.this, WaitActivity.class);
        startActivity(intent);
    }
}