import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class Main {
    private static final int PORT = 9999;
    private static final int BUFFER_SIZE = 4096;
    private static final StringBuilder stringBuilder = new StringBuilder();

    private static final GameManager gameManager = GameManager.getInstance();

    public static void main(String[] args) {
        gameManager.init();
        waitForClients();
    }

    public static void waitForClients() {
        Thread client_thread = new Thread() {
            @Override
            public void run() {
                boolean portIsUsed = false;
                boolean getData;

                String[] userData;
                String id;
                String name;
                int room;

                System.out.printf("%d포트에서 대기 중 ...\r\n", PORT);
                while (!portIsUsed)
                {
                    Socket socket;
                    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                        id = null;
                        name = null;
                        room = 0;

                        Thread.sleep(1000);
                        socket = serverSocket.accept();
                        socket.setSoLinger(true, 0);
                        socket.setKeepAlive(true);
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        String input = bufferedReader.readLine();
                        Header header = Header.getType(input);

                        if (header.getValue().equals(Header.SET_CLIENT_INFO.getValue())) {
                            input = bufferedReader.readLine();
                            if(input == null) {
                                continue;
                            }
                            userData = input.split(",");
                            id = userData[0];
                            name = userData[1];
                            room = Integer.parseInt(userData[2]);

                            if (name == null || name.length() < 2) {
                                name = "user";
                            }
                            if (id == null || id.length() != 36) {
                                id = generateClientId();
                                stringBuilder.setLength(0);
                                socket.getOutputStream().write(stringBuilder.append(Header.SET_CLIENT_INFO.getValue()).append("\n").append(id)
                                        .append("\n").append(name).append("\n").toString().getBytes());
                                socket.getOutputStream().flush();
                            }
                            System.out.printf("%s in %d :%s\r\n", name, room, id);
                        }
                        else {
                            continue;
                        }
                        if (socket.getInputStream().available() > 0){
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int getBytes = socket.getInputStream().read(buffer);
                        }

                        System.out.printf("%s connected\r\n", socket.getInetAddress());
                        gameManager.connClient(socket, id, name, room);
                    } catch (IOException e) {
                        System.out.printf("%s\r\n", "이미 사용중인 포트입니다!!!");
                        portIsUsed = true;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
        client_thread.start();
    }

    public static String generateClientId() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }
}