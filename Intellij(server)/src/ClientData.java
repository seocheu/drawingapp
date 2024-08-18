import java.io.*;
import java.net.Socket;

public class ClientData extends Thread{
    private RoomControllable roomManager;
    private final Object lock = new Object();

    private final Socket socket;
    private String client_id;
    private String client_name;
    private long lastReceivedTime;
    private boolean isAlive;

    public ClientData(Socket socket, String id, String name) {
        this.socket = socket;
        this.client_id = id;
        this.client_name = name;
        lastReceivedTime = System.currentTimeMillis();
    }

    public String getClient_name() {
        return client_name;
    }

    public void setRoomManager(RoomControllable roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void run() {
        BufferedReader bufferedReader = null;
        isAlive = true;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            int roomID;
            String roomName;
            int endTime;
            int maxClient;
            int question;
            String chat;
            String draw;
            while (isAlive) {
                if(bufferedReader.ready()) {
                    String input = bufferedReader.readLine();
                    if (input == null) {
                        continue;
                    }
                    Header header = Header.getType(input);
                    System.out.printf("%s\r\n", header.name());

                    try {
                        switch (header) {
                            case ERROR: initScreen(); break;
                            case HEARTBEAT: connectCheck(); break;
                            case REQUEST_CLIENT_INFO: break;
                            case SET_CLIENT_INFO: bufferedReader.readLine();
                                client_name = bufferedReader.readLine(); break;
                            case CHAT:
                                chat = bufferedReader.readLine();
                                if(roomManager.compareAnswer(chat)) {
                                    roomManager.sendAnotherClients(null, Header.NEXT_STAGE, client_name + " " + chat  + "\n");
                                } else {
                                    roomManager.sendAnotherClients(null, Header.CHAT, client_name + ": " + chat  + "\n");
                                } break;
                            case ANSWER: roomManager.setAnswer(bufferedReader.readLine()); break;
                            case DRAW_START:
                                draw = bufferedReader.readLine() + "\n";
                                draw += bufferedReader.readLine() + "\n";
                                roomManager.sendAnotherClients(this, Header.DRAW_START, draw); break;
                            case DRAW:
                                draw = bufferedReader.readLine() + "\n";
                                draw += bufferedReader.readLine() + "\n";
                                roomManager.sendAnotherClients(this, Header.DRAW, draw); break;
                            case REFRESH_MAIN: roomManager.requestSimpleInfo(this); break;
                            case REFRESH_ROOM: roomManager.requestDetailInfo(this); break;
                            case CREATE_ROOM: roomName = bufferedReader.readLine();
                                endTime = Integer.parseInt(bufferedReader.readLine());
                                maxClient = Integer.parseInt(bufferedReader.readLine());
                                question = Integer.parseInt(bufferedReader.readLine());
                                createRoom(roomName, endTime, maxClient, question); break;
                            case JOIN_ROOM: roomID = Integer.parseInt(bufferedReader.readLine());
                                joinRoom(roomID); break;
                            case LEAVE_ROOM:
                                roomManager.leaveRoomProcess(this); break;
                            case PLAY_GAME: roomManager.startGame(); break;
                            case START_STAGE: break;
                            case GAME_DATA: roomManager.requestDetailInfo(this); break;
                            case NEXT_STAGE: break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.printf("%s, 송신 데이터 처리 중 number format 에러\r\n", client_name);
                    } catch (NullPointerException e) {
                        System.out.printf("%s, 송신 데이터 처리 중 null pointer 에러\r\n", client_name);
                    }
                }
            }
        } catch (IOException e) {
            System.out.printf("%s\r\n", "소켓 getInputStream 오류");
            isAlive = false;
        } finally {
            if( bufferedReader != null) {
                try {
                    bufferedReader.close();
                    socket.close();
                    roomManager.removeClientProcess(ClientData.this);
                } catch (IOException e) {
                    System.out.printf("%s\r\n", "BufferedReader is not initialized");
                }
            }
        }
    }

    public void sendMessage(Header header, String message) {
        synchronized (lock) {
            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(header.getValue().getBytes());
                outputStream.write("\n".getBytes());
                if (message != null) {
                    outputStream.write(message.getBytes());
                }
                outputStream.flush();
                System.out.printf("%s에게 %s 전달\r\n", client_name, header);
            } catch (IOException e) {
                System.out.printf("%s 에러\r\n", "output");
                roomManager.removeClientProcess(this);
                isAlive = false;
            }
        }
    }

    // 화면 갱신
    private void initScreen() {

    }

    private void connectCheck() {
//        System.out.printf("%s: %d\r\n", client_name, Long.sum(System.currentTimeMillis(), -lastReceivedTime));
        lastReceivedTime = System.currentTimeMillis();
    }

    private void createRoom(String roomName, int endTime, int maxClient, int question) {
        roomManager.createRoomProcess(this, roomName, endTime, maxClient, question);
    }

    private void joinRoom(int roomID) {

        roomManager.joinRoomProcess(this, roomID);
    }

    public void quit() {
        isAlive = false;
    }
}
