package littlejlib.proj;

/**
 * One named "open this project in X" command. The picker renders a column per entry, which is how
 * mm's fixed Code and NetBeans columns are absorbed without hardcoding either.
 * <p>
 * {@code command} uses the same %PATH% placeholder as the primary ideLauncher.
 */
public class IdeLauncher {

    private String name;
    private String command;

    public IdeLauncher() { }

    public IdeLauncher(String name, String command) {
        this.name = name;
        this.command = command;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public boolean isUsable() {
        return name != null && !name.isBlank() && command != null && !command.isBlank();
    }

    @Override
    public String toString() {
        return name + " -> " + command;
    }
}
