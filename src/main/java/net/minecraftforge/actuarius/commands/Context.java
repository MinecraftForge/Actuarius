package net.minecraftforge.actuarius.commands;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import net.minecraftforge.actuarius.util.Monos;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

public class Context {

    private final Message message;
    private final Optional<Snowflake> guildId;
    private final String[] args;
    
    // Cached monos
    private final Mono<Guild> guild;
    private final Mono<MessageChannel> channel;
    private final Optional<User> author;
    private final Mono<Member> member;

    public Context(MessageCreateEvent evt) {
        this(evt.getMessage(), evt.getGuildId(), evt.getMessage().getContent().map(Context::parseArgs).orElse(new String[0]));
    }
    
    public Context(Message message, Optional<Snowflake> guildId, String... args) {
        this.message = message;
        this.guildId = guildId;
        this.args = args;
        
        this.guild = message.getGuild().cache();
        this.channel = message.getChannel().cache();
        this.author = message.getAuthor();
        this.member = message.getAuthorAsMember().cache();
    }
    
    // Copy ctor
    private Context(Message message, Optional<Snowflake> guildId, String[] args, Mono<Guild> guild, Mono<MessageChannel> channel, Optional<User> author, Mono<Member> member) {
        this.message = message;
        this.guildId = guildId;
        this.args = args;
        this.guild = guild;
        this.channel = channel;
        this.author = author;
        this.member = member;
    }
    
    private static final String[] parseArgs(String content) {
        String[] in = content.split("\\s+");
        return withoutFirstN(in, 1);
    }
    
    private static String[] withoutFirstN(String[] in, int n) {
        if (in.length == 0) {
            return in;
        }
        String[] out = new String[in.length - n];
        System.arraycopy(in, n, out, 0, out.length);
        return out;
    }
    
    public Context stripArgs(int amount) {
        return new Context(getMessage(), getGuildId(), withoutFirstN(args, amount), guild, channel, author, member);
    }
    
    public String[] getArgs() {
        return args;
    }
    
    public DiscordClient getClient() {
        return message.getClient();
    }
    
    public Message getMessage() {
        return message;
    }
    
    public Mono<Guild> getGuild() {
        return guild;
    }
    
    public Optional<Snowflake> getGuildId() {
        return guildId;
    }
    
    public Mono<MessageChannel> getChannel() {
        return channel;
    }
    
    public Snowflake getChannelId() {
        return getMessage().getChannelId();
    }
    
    public Optional<User> getAuthor() {
        return author;
    }
    
    public Optional<Snowflake> getAuthorId() {
        return getAuthor().map(User::getId);
    }
    
    public Mono<Member> getMember() {
        return member;
    }
    
    public Mono<String> getDisplayName() {
        return getMember().map(Member::getDisplayName)
                .switchIfEmpty(Mono.justOrEmpty(getAuthor().map(User::getUsername)))
                .switchIfEmpty(message.getWebhook().flatMap(w -> Mono.justOrEmpty(w.getName())))
                .defaultIfEmpty("Unknown");
    }
    
    public Mono<String> getDisplayName(User user) {
        return Mono.justOrEmpty(getGuildId())
                .flatMap(user::asMember)
                .map(Member::getDisplayName)
                .defaultIfEmpty(user.getUsername());
    }
    
    public Mono<Message> reply(String message) {
        return getMessage().getChannel()
            .transform(Monos.flatZipWith(sanitize(message), (chan, msg) -> chan.createMessage(m -> m.setContent(msg))));
    }
    
    public Mono<Message> progress(String message) {
        return reply(message).transform(this::andThenType);
    }
    
    @Deprecated
    public Disposable replyFinal(String message) {
        return reply(message).subscribe();
    }
    
    public Mono<Message> reply(Consumer<? super EmbedCreateSpec> message) {
        return getMessage().getChannel().flatMap(c -> c.createMessage(m -> m.setEmbed(message)));
    }
    
    public Mono<Message> progress(Consumer<? super EmbedCreateSpec> message) {
        return reply(message).transform(this::andThenType);
    }
    
    public <T> Mono<T> andThenType(Mono<T> after) {
        return after.flatMap(o -> getChannel().flatMap(c -> c.type()).thenReturn(o));
    }
    
    @Deprecated
    public Disposable replyFinal(Consumer<? super EmbedCreateSpec> message) {
        return reply(message).subscribe();
    }
    
    public <T> Mono<T> error(String message) {
        return Mono.error(new CommandException(message));
    }
    
    public <T> Mono<T> error(Throwable cause) {
        return Mono.error(new CommandException(cause));
    }
    
    public <T> Mono<T> error(String message, Throwable cause) {
        return Mono.error(new CommandException(message, cause));
    }
    
    public Mono<String> sanitize(String message) {
        return getGuild().flatMap(g -> sanitize(g, message)).switchIfEmpty(Mono.just(message));
    }
    
    public static Mono<String> sanitize(Channel channel, String message) {
        if (channel instanceof GuildChannel) {
            return ((GuildChannel) channel).getGuild().flatMap(g -> sanitize(g, message));
        }
        return Mono.just(message);
    }
    
    private static final Pattern DISCORD_MENTION = Pattern.compile("<@&?!?([0-9]+)>");

    public static Mono<String> sanitize(@Nullable Guild guild, String message) {        
        Mono<String> result = Mono.just(message);
        if (guild == null) return result;
        
        Matcher matcher = DISCORD_MENTION.matcher(message);
        while (matcher.find()) {
            final String match = matcher.group();
            Snowflake id = Snowflake.of(matcher.group(1));
            Mono<String> name;
            if (match.contains("&")) {
                name = guild.getClient().getRoleById(guild.getId(), id).map(r -> "the " + r.getName());
            } else {
                Mono<Member> member = guild.getMembers().filter(p -> p.getId().equals(id)).single();
                if (match.contains("!")) {
                    name = member.map(Member::getDisplayName).map(n -> n.replaceAll("@", "@\u200B"));
                } else {
                    name = member.map(Member::getUsername);
                }
            }

            result = result.flatMap(m -> name.map(n -> m.replace(match, n)));
        }
        return result.map(s -> s.replace("@here", "everyone").replace("@everyone", "everyone").replace("@", "@\u200B"));
    }}
