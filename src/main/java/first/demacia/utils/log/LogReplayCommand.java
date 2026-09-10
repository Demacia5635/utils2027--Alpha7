package first.demacia.utils.log;

import org.wpilib.command2.Command;

public class LogReplayCommand extends Command {

    private boolean hasRun = false;

    @Override
    public void initialize() {
        hasRun = false;
    }

    @Override
    public void execute() {
        if (!hasRun) {
            LogReplay.getInstance();
            LogReplay.loadLogs();
            hasRun = true;
        }
    }

    @Override
    public boolean isFinished() {
        return hasRun;
    }

    @Override
    public boolean runsWhenDisabled() {
        return true;
    }
}