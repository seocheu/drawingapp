public interface GameControllable {
    public static final int LOBBY_ID = 0;
    public static final int BUFFER_SIZE = 4096;
    int createRoomProcess(ClientData client, String roomName, int endTime, int maxClient, int question);
    boolean joinRoomProcess(ClientData client, int roomID);
    void removeRoomProcess(int roomID);
    String getRoomsInfo();
    void backLobby(ClientData client);
}
