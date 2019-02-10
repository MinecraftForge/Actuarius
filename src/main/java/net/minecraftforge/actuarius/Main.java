package net.minecraftforge.actuarius;

import java.io.IOException;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.util.Snowflake;
import net.minecraftforge.actuarius.commands.Command;
import net.minecraftforge.actuarius.commands.CommandException;
import net.minecraftforge.actuarius.commands.CommandLabel;
import net.minecraftforge.actuarius.commands.CommandRepoInfo;
import net.minecraftforge.actuarius.commands.CommandTree;
import net.minecraftforge.actuarius.commands.Context;
import net.minecraftforge.actuarius.config.ConfigData;
import net.minecraftforge.actuarius.config.ConfigReader;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

public class Main extends CommandTree {
    
    public static ConfigData cfg = new ConfigData();

    public static void main(String[] unused) throws IOException {
        Security.addProvider(new BouncyCastleProvider());

        ConfigReader cfgReader = new ConfigReader();
        cfgReader.load();
        cfg = cfgReader.getData();
        
        String token = cfg.discord.token;
       
        if (token == null) {
            throw new IllegalArgumentException("No token provided.");
        }
        
        Hooks.onOperatorDebug();
        
        DiscordClient client = new DiscordClientBuilder(token).build();
        
        CommandTree rootCommand = new Main();
        rootCommand
            .withNode("help", (ctx) -> {
                String reply;
                if (ctx.getArgs().length == 0) {    
                    reply = "Available commands: " + rootCommand.getSubCommands();
                } else {
                    String cmdName = ctx.getArgs()[0];
                    Command cmd = rootCommand.getSubcommand(cmdName);
                    if (cmd == null) {
                        throw new CommandException("No such command.");
                    }
                    reply = "**" + cmdName + " -- " + cmd.description() + "**\nUsage: " + cmdName + " " + cmd.usage();
                }
                
                return ctx.getChannel().flatMap(c -> c.createMessage(spec -> spec.setContent(reply)));
            })
            .withNode("info", new CommandRepoInfo())
            .withNode("label", new CommandLabel());
        
        Mono<Void> commandResults = client.getEventDispatcher().on(MessageCreateEvent.class)
                
            // Find all messages mentioning us
            .filterWhen(e -> e.getMessage().getUserMentions().next().map(u -> client.getSelfId().map(u.getId()::equals).orElse(false)))
            
            // Filter for messages where the mention is the start of the content
            .filterWhen(e -> Mono.justOrEmpty(client.getSelfId()).flatMap(id -> isMentionFirst(id, e.getMessage())))
            .filter(e -> e.getMessage().getContent().isPresent())
            .filter(e -> e.getGuildId().isPresent())
            .map(Context::new)
            .flatMap(ctx -> {
                try { 
                    return rootCommand.invoke(ctx)
                        .onErrorResume(CommandException.class, e -> ctx.reply("Could not process command: " + e.getMessage()).then(Mono.empty()))
                        .onErrorResume(e -> ctx.reply("Unexpected error: " + e).then(Mono.empty()));
                } catch (Exception e) {
                    return ctx.reply("Unexpected error: " + e);
                }
            })
            .then();
        
        // block() is required to prevent the VM exiting prematurely
        client.login()
              .and(commandResults)
              .block();
    }
    
    private static Mono<Boolean> isMentionFirst(Snowflake id, Message message) {
        return Mono.just(message)
                .filterWhen(m -> m.getUserMentions()
                                    .map(u -> u.getId().equals(id)))
                .map(m -> m.getContent().map(s -> s.matches("^<!?@" + id.asLong() + ">.*$")).orElse(false));
    }
}
