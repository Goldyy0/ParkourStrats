package me.texyle.startreminders.input;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

import me.texyle.startreminders.utils.ChatUtils;

/** Client-side command behind the [Finish] / [Cancel] buttons shown in chat while taps are recorded. */
public class TapRecordCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return TapRecorder.COMMAND;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + TapRecorder.COMMAND + " <finish|cancel>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!TapRecorder.isRecording()) {
            ChatUtils.warning("Not recording taps.");
            return;
        }

        if (args.length > 0 && "cancel".equalsIgnoreCase(args[0])) {
            TapRecorder.cancel();
        } else {
            TapRecorder.finish();
        }
    }
}
