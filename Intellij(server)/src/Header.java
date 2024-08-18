public enum Header {
    ERROR("0"),
    HEARTBEAT("1"),
    REQUEST_CLIENT_INFO("2"),
    SET_CLIENT_INFO("3"),
    CHAT("5"),
    ANSWER("6"),
    DRAW_START("7"),
    DRAW("8"),
    REFRESH_MAIN("m"),
    REFRESH_ROOM("r"),
    CREATE_ROOM("c"),
    JOIN_ROOM("j"),
    LEAVE_ROOM("l"),
    PLAY_GAME("p"),
    START_STAGE("s"),
    GAME_DATA("g"),
    NEXT_STAGE("n");

    private final String value;
    Header(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Header getType(String input) {
        for(Header header : Header.values()) {
            if(header.getValue().equals(input)) {
                return header;
            }
        }
        System.out.printf("식별되지 않은 헤더(%s)\r\n", input);
        return ERROR;
    }
}
