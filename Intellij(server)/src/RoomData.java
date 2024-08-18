import java.util.concurrent.ConcurrentLinkedQueue;

public class RoomData extends Thread implements RoomControllable {
    private final ConcurrentLinkedQueue<ClientData> clients = new ConcurrentLinkedQueue<>();
    private final GameControllable gameManager;
    private StringBuilder stringBuilder = new StringBuilder();

    private final int roomID;
    private String roomName;
    private int endTime;
    private int maxClient;
    private int question;
    private String answer = null;
    private int count;

    private boolean isRun;

    public RoomData(GameControllable gameManager, int roomID, String roomName, int endTIme, int maxClient, int question) {
        this.gameManager = gameManager;
        this.roomID = roomID;
        this.roomName = roomName;
        this.endTime = endTIme;
        this.maxClient = maxClient;
        this.question = question;
    }

    @Override
    public void run() {
        isRun = true;
        while (isRun) {
            try {
                System.out.printf("%s %s\r\n", roomName, clients.size());
                sendAnotherClients(null, Header.HEARTBEAT, null);
                sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void joinRoomProcess(ClientData client, int roomID) {
        if(gameManager.joinRoomProcess(client, roomID)) {
            client.sendMessage(Header.JOIN_ROOM, roomID + "\n");
            clients.remove(client);
        }
    }

    @Override
    public void leaveRoomProcess(ClientData client) {
        removeClientProcess(client);
        gameManager.backLobby(client);
    }

    @Override
    public void createRoomProcess(ClientData client, String roomName, int endTime, int maxClient, int question) {
        if(roomID == gameManager.LOBBY_ID) {
            int newRoomID = gameManager.createRoomProcess(client, roomName, endTime, maxClient, question);
            client.sendMessage(Header.CREATE_ROOM, String.valueOf(newRoomID) + "\n");
            clients.remove(client);
        }
    }

    @Override
    public void disperseRoomProcess() {
        gameManager.removeRoomProcess(roomID);
        isRun = false;
    }

    @Override
    public void removeClientProcess(ClientData client) {
        clients.remove(client);
        if(roomID != 0 && clients.isEmpty()) {
            disperseRoomProcess();
        }
    }

    @Override
    public void sendAnotherClients(ClientData sender, Header header, String message) {
        for(ClientData client : clients) {
            if(!client.equals(sender)) {
                client.sendMessage(header, message);
            }
        }
    }

    @Override
    public void requestSimpleInfo(ClientData client) {
        client.sendMessage(Header.REFRESH_MAIN, gameManager.getRoomsInfo());
    }

    @Override
    public void requestDetailInfo(ClientData requester) {
        stringBuilder.setLength(0);
        stringBuilder.append(clients.size()).append("\n").append(roomName).append("\n");
        for (ClientData client : clients) {
            stringBuilder.append(client.getClient_name()).append("\n");
        }
        System.out.printf("%s", stringBuilder.toString());
        requester.sendMessage(Header.REFRESH_ROOM, stringBuilder.toString());
    }

    public String simpleInfo() {
        if(clients.size() == maxClient) {
            return null;
        } else {
            stringBuilder.setLength(0);
            ClientData client = clients.peek();
            return stringBuilder.append(roomID).append(". ").append(roomName).append("   ").append(client.getClient_name())
                    .append(" ").append(clients.size()).append("/").append(maxClient).append("\n").toString();
        }
    }

    @Override
    public void startGame() {
        answer = null;
        ClientData tester = clients.poll();
        tester.sendMessage(Header.PLAY_GAME, "t\n");
        sendAnotherClients(null, Header.PLAY_GAME, "r\n");
        clients.offer(tester);
    }

    @Override
    public void startStage() {

    }

    @Override
    public void setAnswer(String answer) {
        this.answer = answer;
    }

    @Override
    public boolean compareAnswer(String answer) {
        if(answer.equals(this.answer)) {
            return true;
        } else {
            return false;
        }
    }

    public void addClient(ClientData client) {
        client.setRoomManager(this);
        clients.offer(client);
        System.out.printf(roomName + "에 " + client.getClient_name() + "추가\n");
    }
}