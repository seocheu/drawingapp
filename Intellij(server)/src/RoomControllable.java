public interface RoomControllable {
    public static final int SIMPLE = 1;
    public static final int DETAIL = 2;
    void joinRoomProcess(ClientData client, int roomID);
    void leaveRoomProcess(ClientData client);
    void createRoomProcess(ClientData client, String roomName, int endTime, int maxClient, int question);
    void disperseRoomProcess();
    void removeClientProcess(ClientData client);
    void sendAnotherClients(ClientData sender, Header header, String message);
    void requestSimpleInfo(ClientData client);
    void requestDetailInfo(ClientData client);
    void startGame();
    void startStage();
    void setAnswer(String answer);
    boolean compareAnswer(String answer);
}
