package town.championsofequestria.chat.api;

import java.util.ArrayList;

import org.bukkit.ChatColor;

import town.championsofequestria.chat.ChatPlugin;

public class PrivateChannel extends Channel {

    private ArrayList<Chatter> target;

    PrivateChannel(Chatter target) {
        this.target = new ArrayList<Chatter>(1);
        this.target.add(target);
    }

    public String formatPrivateFromMessage(Chatter sender, String message) {
        return ChatPlugin.getPlugin().getSettings().getPrivateMessageFormat().replace("{convoaddress}", "From").replace("{convopartner}", sender.getName()).replace("{msg}", message);
    }

    public String formatPrivateLogMessage(Chatter sender, String message) {
        return ChatColor.stripColor(ChatPlugin.getPlugin().getSettings().getPrivateMessageFormat().replace("{convoaddress}", sender.getName() + " ->").replace("{convopartner}", getTargetName()).replace("{msg}", message));
    }

    public String formatPrivateToMessage(String message) {
        return ChatPlugin.getPlugin().getSettings().getPrivateMessageFormat().replace("{convoaddress}", "To").replace("{convopartner}", getTargetName()).replace("{msg}", message);
    }

    @Override
    public String getName() {
        return "PM";
    }

    @Override
    public String getQuickMessageCommand() {
        throw new UnsupportedOperationException();
    }

    public Chatter getTarget() {
        return target.get(0);
    }

    private String getTargetName() {
        return getTarget().getName();
    }

    @Override
    public boolean isMuted(Chatter chatter) {
        return target.get(0).isIgnoring(chatter);
    }

    @Override
    protected void sendChannelMessage(ArrayList<Chatter> recipents, String message) {
        recipents.forEach((chatter) -> chatter.sendMessage(message));
    }

    @Override
    public void sendChatMessage(Chatter sender, String message) {
        sender.sendMessage(formatPrivateToMessage(message));
        sendChannelMessage(target, formatPrivateFromMessage(sender, message));
        this.logChat(formatPrivateLogMessage(sender, message));
    }
}
