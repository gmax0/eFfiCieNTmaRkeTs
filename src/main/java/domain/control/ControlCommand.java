package domain.control;

import lombok.Getter;

import java.util.Map;

public class ControlCommand {
    public static final Command SILENT = new Command("0", "Awaiting Command.\n");
    public static final Command SHUTDOWN = new Command("1", "Application Shutdown.\n");
    public static final Command REBALANCE = new Command("2", "Rebalance to USD.\n");
    public static final Command RECONNECT = new Command("3", "Reconnecting to all websockets.\n");

    private static final Map<String, Command> commands = Map.of(
            SILENT.key, SILENT,
            SHUTDOWN.key, SHUTDOWN,
            REBALANCE.key, REBALANCE,
            RECONNECT.key, RECONNECT
    );

    @Getter
    public static class Command {
        final String key;
        final String message;

        public Command(String key, String message) {
            this.key = key;
            this.message = message;
        }
    }

    public static Command getCommandByKey(String key) {
        return key.equals("0") ? null : commands.get(key);
    }
}
