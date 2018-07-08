package org.spongepowered.felix.command.custom.verifyrole;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Joiner;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.JSONObject;
import org.spongepowered.felix.command.custom.CustomCommand;
import org.spongepowered.felix.platform.DiscordPlatform;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IPrivateChannel;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.RequestBuffer;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandRole implements CustomCommand {

    private String baseDiscordURL;
    private String baseOreURL;

    private String pluginDeveloperRole;

    private static final String VERIFY_ROLE = "verify";
    private static final String TOKEN = "forum-token";
    private static final String[] SUBCOMMANDS = {VERIFY_ROLE, TOKEN};
    private int token_length = 20;

    private String tokenCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private Map<String, TokenData> discordUsernameToToken = new HashMap<>();
    private String discourse_api_key;
    private String discourse_api_username;

    private HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();

    @Override
    public void process(String[] args, MessageReceivedEvent event) {

        if (!(event.getChannel() instanceof IPrivateChannel)) {
            return;
        }
        if (args.length == 0) {
            RequestBuffer.request(() -> event.getChannel().sendMessage("Missing argument!"));
            return;
        }

        String subCommand = args[1];
        if (subCommand.equals(VERIFY_ROLE)) {
            if (args.length < 3) {
                RequestBuffer.request(() -> event.getChannel().sendMessage("Provide your Sponge forums username!"));
                return;
            }
            String forumUsername = args[2];
            if (forumUsername.contains(",")) {
                RequestBuffer.request(() -> event.getChannel().sendMessage("Forum username cannot contain a comma!"));
                return;
            }
            this.sendForumMessage(event, forumUsername);
        } else if (subCommand.equals(TOKEN)) {
            if (args.length < 3) {
                RequestBuffer.request(() -> event.getChannel().sendMessage("Missing token!"));
                return;
            }
            String token = args[2];
            this.onForumToken(event, token);
        } else {
            RequestBuffer
                    .request(() -> event.getChannel().sendMessage(String.format("Unknown subcommand '%s'. Available commands: %s", subCommand,
                            Joiner.on(",").join(SUBCOMMANDS))));
        }
    }

    private void sendForumMessage(MessageReceivedEvent event, String forumUsername) {
        String token = this.getRandomToken();

        this.storeToken(event, new TokenData(token, forumUsername));
        JsonObject messageRequest = new JsonObject();
        messageRequest.addProperty("title", "Role verification token");
        messageRequest.addProperty("topic_id", 0);
        messageRequest.addProperty("raw", String.format("Send the following private message to the bot: role verify %e", token));
        messageRequest.addProperty("category", 0);
        messageRequest.addProperty("target_usernames", forumUsername);
        messageRequest.addProperty("archetype", "private_message");

        try {
            String url = baseDiscordURL + "/posts.josn? " + String.format("api_key=%s&api_username=%s", this.discourse_api_key, this.discourse_api_username);
            HttpRequest request = this.requestFactory.buildPostRequest(new GenericUrl(url), new ByteArrayContent("multipart/form-data", messageRequest.toString().getBytes("UTF-8")));
            HttpResponse response = request.execute();


            if (!response.isSuccessStatusCode()) {
                String responseText = response.parseAsString();
                DiscordPlatform.LOGGER.error(String.format("Failed to send private message to %s: %s", forumUsername, responseText));
                RequestBuffer.request(() -> event.getChannel().sendMessage("Failed to send forum private message"));
                return;
            }

            RequestBuffer.request(() -> event.getChannel().sendMessage("Check your sponge forums account for a new private message!"));
            return;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getRandomToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder token = new StringBuilder();
        int tokenCharactersLen = this.tokenCharacters.length();
        for (int i = 0; i < token_length; i++) {
            token.append(this.tokenCharacters.charAt(random.nextInt(tokenCharactersLen)));
        }
        return token.toString();
    }

    private void storeToken(MessageReceivedEvent event, TokenData token) {
        this.discordUsernameToToken.put(event.getAuthor().getStringID(), token);
    }

    private TokenData getToken(MessageReceivedEvent event) {
        return this.discordUsernameToToken.get(event.getAuthor().getStringID());
    }

    private void clearToken(MessageReceivedEvent event) {
        this.discordUsernameToToken.remove(event.getAuthor().getStringID());
    }

    private void onForumToken(MessageReceivedEvent event, String forumToken) {
        TokenData tokenData = this.getToken(event);
        if (forumToken.equals(tokenData.token)) {
            RequestBuffer.request(() -> event.getChannel().sendMessage("Granting roles!"));
            this.onForumVerify(event, forumToken);
            this.clearToken(event);
        } else {
            RequestBuffer.request(() -> event.getChannel().sendMessage("Invalid token!"));
        }
    }

    private void onForumVerify(MessageReceivedEvent event, String forumUsername) {
        this.validatePluginDeveloperRole(event, forumUsername);
    }

    private void validatePluginDeveloperRole(MessageReceivedEvent event, String forumUsername) {
        String url = this.baseOreURL + "/api/v1/users/" + forumUsername;
        try {
            HttpResponse resp = this.requestFactory.buildGetRequest(new GenericUrl(url)).execute();
            if (!resp.isSuccessStatusCode()) {
                DiscordPlatform.LOGGER.error(String.format("Failed to get Ore projects for %s: %s", forumUsername, resp.parseAsString()));
                RequestBuffer.request(() -> event.getChannel().sendMessage("Failed to get Ore projects for " + forumUsername));
                return;
            }

            JsonObject user = new JsonParser().parse(resp.parseAsString()).getAsJsonObject();
            JsonArray projects = user.getAsJsonArray("projects");
            if (projects.size() > 0) {
                this.grantPluginDeveloperRole(event);
            } else {
                RequestBuffer.request(() -> event.getChannel().sendMessage("You have no Ore projects!"));
                return;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void grantPluginDeveloperRole(MessageReceivedEvent event) {
        event.getClient().getOurUser().addRole(this.getPluginDeveloperRole(event));
        RequestBuffer.request(() -> event.getChannel().sendMessage("Successfully granted plugin developer role!"));
    }

    private IRole getPluginDeveloperRole(MessageReceivedEvent event) {
        List<IRole> roles = event.getGuild().getRolesByName(this.pluginDeveloperRole);
        if (roles.size() != 1) {
            throw new IllegalStateException(String.format("Expected one role with the name %s, but found %s", this.pluginDeveloperRole, roles));
        }
        return roles.get(0);
    }

    static class TokenData {
        String token;

        public TokenData(String token, String forumUsername) {
            this.token = token;
            this.forumUsername = forumUsername;
        }

        String forumUsername;
    }
}
