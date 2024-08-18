import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
public class GameManager implements GameControllable{
    private static final GameManager instance = new GameManager();
    private GameManager() {}
    public static GameManager getInstance() {
        return instance;
    }

    private static int count = 1;

    private static final RoomData lobby = new RoomData(instance, LOBBY_ID, "LOBBY", -1, -1, -1);
    private static final ConcurrentHashMap<Integer, RoomData> rooms = new ConcurrentHashMap<>();
    private static final StringBuilder stringBuilder = new StringBuilder();

    public void init() {
        lobby.start();
    }

    @Override
    public int createRoomProcess(ClientData client, String roomName, int endTime, int maxClient, int question) {
        rooms.put(count, new RoomData(instance, count, roomName, endTime, maxClient, question));
        RoomData room = rooms.get(count);
        room.addClient(client);
        room.start();
        return count++;
    }

    @Override
    public boolean joinRoomProcess(ClientData client, int roomID) {
        RoomData room;
        if((room = rooms.get(roomID)) != null) {
            room.addClient(client);
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void removeRoomProcess(int roomID) { // 방 삭제
        rooms.remove(roomID);
    }

    public void connClient(Socket socket, String id, String name, int roomID) {
        ClientData client = new ClientData(socket, id, name);
        if(roomID == 0) {
            lobby.addClient(client);
        }
        else {
            RoomData room;
            if((room = rooms.get(roomID)) != null) {
                room.addClient(client);
            }
            else {
                lobby.addClient(client);
            }
        }
        client.start();
    }

    @Override
    public String getRoomsInfo() {
        int count = 0;
        String info;
        stringBuilder.setLength(0);
        for(RoomData room : rooms.values()) {
            info = room.simpleInfo();
            if (info == null) {
                count++;
            } else {
                stringBuilder.append(info);
            }
        }
        return (rooms.size() - count) + "\n" + stringBuilder.toString();
    }

    @Override
    public void backLobby(ClientData client) {
        lobby.addClient(client);
    }
}
