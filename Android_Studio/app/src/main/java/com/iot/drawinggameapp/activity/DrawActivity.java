package com.iot.drawinggameapp.activity;

import android.app.Dialog;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

public class DrawActivity extends AppCompatActivity {
    private static final int PIXEL_SIZE = 16;
    private CustomApplication application;
    private ExecutorService executors;
    private Handler handler;
    private StringBuilder stringBuilder = new StringBuilder();

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
    private LinearLayout colorLayout;

    // 문제 입력 다이얼로그
    private Dialog dialog;
    private EditText editText;
    private Button button;

    // 채팅
    private ListView listView_chat;
    private List<String> chats = new ArrayList<>();
    private ArrayAdapter<String> chat_adapter;

    // 유저
    private ListView listView_user;
    private List<String> users = new ArrayList<>();
    private ArrayAdapter<String> user_adapter;

    private Header currentHeader = null;
    private List<String> data = new ArrayList<>();
    private int dataCount;
    private int itemCount;

    private TextView topTextView;

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
        setContentView(R.layout.activity_draw);

        application = CustomApplication.getInstance();
        executors = application.getExecutors();
        handler = new Handler(Looper.getMainLooper());

        imageView = findViewById(R.id.imageView);
        imageView.setClickable(true);
        original = Bitmap.createBitmap(PIXEL_SIZE, PIXEL_SIZE, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(original);
        canvas.drawColor(Color.WHITE);
        imageView.setImageBitmap(original);

        listView_user = findViewById(R.id.listView_user);
        listView_chat = findViewById(R.id.listView_Chat);
        chat_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chats);
        user_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, users);
        listView_chat.setAdapter(chat_adapter);
        listView_user.setAdapter(user_adapter);

        topTextView = findViewById(R.id.textView_top);

        setDraw();
        setColor();
        setDialog();
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
    }

    @Override
    protected void onStop() {
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
                    }  else if(currentHeader == Header.DRAW_START || currentHeader == Header.DRAW) {
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
                    for(int i = 1; i <= itemCount; i++) {
                        users.add(data.get(i));
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            topTextView.setText(data.get(0));
                            user_adapter.notifyDataSetChanged();
                        }
                    }); break;
            }

            currentHeader = null;
        });
    }

    private void setDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_answer);
        dialog.show();
        editText = dialog.findViewById(R.id.editText);
        button = dialog.findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                executors.submit(()->{
                    socketService.sendMessage(Header.ANSWER, editText.getText().toString() + "\n");
                });
                dialog.dismiss();
            }
        });
    }

    private void setDraw() {
        imageView.setOnTouchListener(new View.OnTouchListener() {
            float x;
            float y;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                float touchX = motionEvent.getX() - 40; // +로 치우쳐진 가로값 보정
                float touchY = motionEvent.getY();

                x = touchX * PIXEL_SIZE / imageView.getHeight();
                y = touchY * PIXEL_SIZE / imageView.getHeight();

                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        drawStart(); break;
                    case MotionEvent.ACTION_MOVE:
                        draw(); break;
                    case MotionEvent.ACTION_UP:
                        draw(); break;
                }
                return false;
            }

            public void drawStart() {
                paint = new Paint();
                paint.setColor(color);
                paint.setStyle(Paint.Style.STROKE);
                path.reset();
                path.moveTo(x, y);
                prevX = x;
                prevY = y;
                original.setPixel((int)x, (int)y, color);
                resize = Bitmap.createScaledBitmap(original, 320, 320, false);
                imageView.setImageBitmap(resize);
                stringBuilder.setLength(0);
                executors.submit(()->{
                    socketService.sendMessage(Header.DRAW_START, stringBuilder.append(color).append("\n")
                            .append(x).append(" ").append(y).append("\n").toString()
                    );
                });
            }

            public void draw() {
                path.lineTo(x, y); canvas.drawPath(path, paint);
                resize = Bitmap.createScaledBitmap(original, 320, 320, false);
                imageView.setImageBitmap(resize);
                stringBuilder.setLength(0);
                executors.submit(()->{
                    socketService.sendMessage(Header.DRAW, stringBuilder.append(color).append("\n")
                            .append(x).append(" ").append(y).append("\n").toString()
                    );
                });
                prevX = x;
                prevY = y;
            }
        });
    }

    private void setColor() {
        colorLayout = findViewById(R.id.color_layout);

        for(int i = 0; i < colorLayout.getChildCount(); i++) {
            View colorView = colorLayout.getChildAt(i);
            colorView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    color = ((ColorDrawable)colorView.getBackground()).getColor();
                }
            });
        }
    }
}