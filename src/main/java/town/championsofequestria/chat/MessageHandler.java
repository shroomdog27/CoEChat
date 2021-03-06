package town.championsofequestria.chat;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.sweetiebelle.lib.permission.PermissionManager;

import town.championsofequestria.chat.api.Channel;
import town.championsofequestria.chat.api.ChatResult;
import town.championsofequestria.chat.api.Chatter;
import town.championsofequestria.chat.api.StandardChatter;
import town.championsofequestria.chat.api.PrivateChannel;
import town.championsofequestria.chat.api.StandardChannel;
import town.championsofequestria.chat.api.event.ChannelChatEvent;
import town.championsofequestria.chat.api.event.ChannelPrivateMessageEvent;
import town.championsofequestria.chat.manager.ChatterManager;

public class MessageHandler {

    private static final Pattern tagPattern = Pattern.compile("(\\{\\w+\\})");
    private ChatterManager chatterManager;
    private PermissionManager lpManager;

    public MessageHandler(PermissionManager lpManager, ChatterManager chatterManager) {
        this.lpManager = Objects.requireNonNull(lpManager);
        this.chatterManager = Objects.requireNonNull(chatterManager);
    }

    private boolean checkEventResult(ChatResult result, StandardChatter player, StandardChannel channel) {
        switch (result) {
            case NO_PERMISSION: {
                player.sendMessage(ChatColor.RED + "You don't have permission to chat in " + channel.getName());
                return false;
            }
            case NO_SUCH_CHANNEL: {
                player.sendMessage(ChatColor.RED + "You cannot speak in " + channel.getName() + " at this time.");
                return false;
            }
            case MUTED: {
                player.sendMessage(ChatColor.RED + "You were muted from this channel!");
                return false;
            }
            case ALLOWED: {
                return true;
            }
        }
        player.sendMessage(ChatColor.RED + "An unknown state has occured.");
        throw new IllegalStateException("End of Switch");
    }

    private boolean checkPMResult(ChatResult result, Chatter sender) {
        switch (result) {
            case NO_PERMISSION: {
                sender.sendMessage(ChatColor.RED + "You don't have permission send private messages");
                return false;
            }
            case MUTED: {
                sender.sendMessage(ChatColor.RED + "You cannot send a message to that person.");
                return false;
            }
            case ALLOWED: {
                return true;
            }
            case NO_SUCH_CHANNEL: {
                sender.sendMessage(ChatColor.RED + "That player isn't online anymore. Use /ch <channel> to focus on another channel.");
                return false;
            }
        }
        sender.sendMessage(ChatColor.RED + "An unknown state has occured.");
        throw new IllegalStateException("End of Switch");
    }

    public String formatMessage(StandardChannel channel, Player player, String format, String message) {
        Matcher matcher = tagPattern.matcher(format);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            matcher.appendReplacement(sb, formatTag(tag.replace("{", "").replace("}", ""), Optional.of(player), channel));
        }
        format = matcher.appendTail(sb).toString();
        format = format.replace("{msg}", message);
        format = format.replaceAll("(?i)&([a-fklmnor0-9])", "\u00a7$1");
        return format;
    }

    private String formatTag(String tag, Optional<Player> player, StandardChannel channel) {
        switch (tag) {
            case "plainsender": {
                return player.isPresent() ? player.get().getName() : "";
            }
            case "sender": {
                return player.isPresent() ? player.get().getDisplayName() : "";
            }
            case "name": {
                return channel.getName();
            }
            case "color": {
                return channel.getColor().toString();
            }
            case "world": {
                return player.isPresent() ? player.get().getWorld().getName() : "";
            }
            case "prefix": {
                return player.isPresent() ? lpManager.getPlayerPrefix(player.get().getUniqueId()) : "";
            }
            case "suffix": {
                return player.isPresent() ? lpManager.getPlayerSuffix(player.get().getUniqueId()) : "";
            }
            case "gprefix": {
                return player.isPresent() ? lpManager.getGroupPrefix(player.get().getUniqueId()) : "";
            }
            case "gsuffix": {
                return player.isPresent() ? lpManager.getGroupSuffix(player.get().getUniqueId()) : "";
            }
            case "group": {
                return player.isPresent() ? lpManager.getPrimaryGroup(player.get().getUniqueId()) : "";
            }
            default : {
                return "{" + tag + "}";
            }
        }
    }

    public String getOrStripColor(StandardChatter sender, StandardChannel channel, String message) {
        Pattern pattern = Pattern.compile("(?i)(&)([0-9a-fk-or])");
        Matcher match = pattern.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (match.find()) {
            if (sender.hasPermissionToColor(channel)) {
                match.appendReplacement(sb, ChatColor.getByChar(match.group(2).toLowerCase()).toString());
            } else {
                match.appendReplacement(sb, "");
            }
        }
        return match.appendTail(sb).toString();
    }

    /**
     * Sends a chat message with the player's default channel being the destination
     * 
     * @param player
     * @param message
     * @param asynchronous
     */
    public void handle(Player player, String message, boolean asynchronous) {
        Optional<StandardChatter> chatter = chatterManager.getChatter(player);
        if (chatter.isPresent())
            handle(chatter.get(), chatter.get().getActiveChannel(), message, asynchronous);
        else
            player.sendMessage("Please wait until you are fully logged in to chat.");
    }

    public void handle(StandardChatter chatter, Channel channel, String message, boolean asynchronous) {
        if (channel == null) {
            chatter.sendMessage(ChatColor.LIGHT_PURPLE + "You aren't currently focused in a channel.");
            return;
        }
        if (channel instanceof PrivateChannel)
            handlePM(chatter, (PrivateChannel) channel, message, asynchronous);
        else
            handleChat(chatter, (StandardChannel) channel, message, asynchronous);
    }

    public void handleChat(StandardChatter chatter, StandardChannel channel, String message, boolean asynchronous) {
        if (ChatPlugin.isNull(message))
            return;
        ChannelChatEvent event = new ChannelChatEvent(chatter, channel, ChatResult.ALLOWED, message, channel.getFormat(), asynchronous);
        Bukkit.getPluginManager().callEvent(event);
        if (!checkEventResult(event.getResult(), chatter, channel))
            return;
        message = event.getMessage();
        message = getOrStripColor(chatter, channel, message);
        message = formatMessage(channel, chatter.getPlayer(), event.getFormat(), message);
        channel.sendChatMessage(chatter, message);
    }

    public void handlePM(Chatter sender, PrivateChannel channel, String message, boolean asynchronous) {
        if (ChatPlugin.isNull(message))
            return;
        ChannelPrivateMessageEvent event = new ChannelPrivateMessageEvent(sender, channel, ChatResult.ALLOWED, message, asynchronous);
        Bukkit.getPluginManager().callEvent(event);
        if (!checkPMResult(event.getResult(), sender))
            return;
        channel.sendChatMessage(sender, message);
        channel.getTarget().setLastChatter(sender);
        socialSpy(sender, channel, message);
    }

    private void socialSpy(Chatter sender, PrivateChannel channel, String message) {
        for (Chatter c : chatterManager.getChatters()) {
            if (c.hasPermissionToSocialSpy()) {
                c.sendMessage(channel.formatPrivateLogMessage(sender, message));
            }
        }
    }
}
