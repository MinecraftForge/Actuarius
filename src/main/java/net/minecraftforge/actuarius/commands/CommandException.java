package net.minecraftforge.actuarius.commands;


public class CommandException extends RuntimeException {

    private static final long serialVersionUID = 7397025139691342944L;
    
    public CommandException(String msg) {
        super(msg);
    }
    
    public CommandException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public CommandException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
