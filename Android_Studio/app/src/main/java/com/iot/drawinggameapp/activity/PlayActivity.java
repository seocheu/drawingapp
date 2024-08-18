package com.iot.drawinggameapp.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.iot.drawinggameapp.CustomApplication;
import com.iot.drawinggameapp.Header;
import com.iot.drawinggameapp.R;
import com.iot.drawinggameapp.SocketService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class PlayActivity extends AppCompatActivity {
    private static final int PIXEL_SIZE = 16;
    private CustomApplication application;
    private ExecutorService executors;
    private Handler handler;
    private StringBuilder stringBuilder = new StringBuilder();

    // 채팅
    private ListView listView_chat;
    private List<String> chats = new ArrayList<>();
    private ArrayAdapter<String> chat_adapter;

    // 유저
    private ListView listView_user;
    private List<String> users = new ArrayList<>();
    private ArrayAdapter<String> user_adapter;

    // 드로잉
    private ImageView imageView;
    private Bitmap original;
    private Bitmap resize;
    private Canvas canvas;
    private Path path = new Path();
    private Paint paint;
    private int color = Color.BLACK;
    private float prevX;
    private float prevY;

    private Header currentHeader = null;
    private List<String> data = new ArrayList<>();
    private int dataCount;
    private int itemCount;

    private TextView topTextView;
    private EditText editText;
    private Button button;

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
        setContentView(R.layout.activity_play);

        application = CustomApplication.getInstance();
        executors = application.getExecutors();
        handler = new Handler(Looper.getMainLooper());

        listView_user = findViewById(R.id.listView_user);
        listView_chat = findViewById(R.id.listView_Chat);
        chat_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chats);
        user_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, users);
        listView_chat.setAdapter(chat_adapter);
        listView_user.setAdapter(user_adapter);

        imageView = findViewById(R.id.imageView);
        imageView.setClickable(true);
        original = Bitmap.createBitmap(PIXEL_SIZE, PIXEL_SIZE, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(original);
        canvas.drawColor(Color.WHITE);
        imageView.setImageBitmap(original);

        topTextView = findViewById(R.id.textView_top);
        editText = findViewById(R.id.editText);
        button = findViewById(R.id.button);

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

        executors.submit(new Runnable() {
            @Override
            public void run() {
                socketService.sendMessage(Header.GAME_DATA, null);
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String input = editText.getText().toString();
                editText.setText("");
                executors.submit(new Runnable() {
                    @Override
                    public void run() {
                        socketService.sendMessage(Header.CHAT, input + "\n");
                    }
                });
            }
        });
    }

    @Override
    protected void onStop() {
        if(isBound) {
            unbindService(connection);
        }
        unregisterReceiver(receiver);

        button.setOnClickListener(null);

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
                    if(currentHeader == Header.REFRESH_ROOM || currentHeader == Header.CHAT ||
                            currentHeader == Header.NEXT_STAGE) {
                        dataCount = 1;
                    } else if (currentHeader == Header.DRAW_START || currentHeader == Header.DRAW) {
                        dataCount = 2;
                    } else {
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
            String[] coordinate = new String[2];

            switch (currentHeader) {
                case ERROR:
                    break;
                case HEARTBEAT:
                    socketService.connectCheck();
                    socketService.sendMessage(Header.GAME_DATA, null);
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
                    users.clear();
                    roomName = data.get(0);
                    for(int i = 1; i <= itemCount; i++) {
                        users.add(data.get(i));
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            topTextView.setText(roomName);
                            user_adapter.notifyDataSetChanged();
                        }
                    }); break;
                case DRAW_START: color = Integer.parseInt(data.get(0));
                    coordinate = data.get(1).split(" ");
                    drawStart(Float.parseFloat(coordinate[0]), Float.parseFloat(coordinate[1]));
                    break;
                case DRAW: color = Integer.parseInt(data.get(0));
                    coordinate = data.get(1).split(" ");
                    draw(Float.parseFloat(coordinate[0]), Float.parseFloat(coordinate[1]));
                    break;
            }

            currentHeader = null;
        });
    }

    public void drawStart(float x, float y) {
        paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        path.reset();
        path.moveTo(x, y);
        prevX = x;
        prevY = y;
        original.setPixel((int)x, (int)y, color);
        resize = Bitmap.createScaledBitmap(original, 320, 320, false);
        handler.post(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(resize);
            }
        });
        stringBuilder.setLength(0);
    }

    public void draw(float x, float y) {
        path.lineTo(x, y); canvas.drawPath(path, paint);
        resize = Bitmap.createScaledBitmap(original, 320, 320, false);
        handler.post(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(resize);
            }
        });
        stringBuilder.setLength(0);
        prevX = x;
        prevY = y;
    }
}