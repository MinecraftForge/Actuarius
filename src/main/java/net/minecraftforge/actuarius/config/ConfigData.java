package net.minecraftforge.actuarius.config;

import java.util.ArrayList;
import java.util.List;

public class ConfigData {
    
    public final DiscordInfo discord;
    public final GithubInfo github;
    public final List<PermissionEntry> grants;
    
    public ConfigData(DiscordInfo discord, GithubInfo github, List<PermissionEntry> grants) {
        this.discord = discord;
        this.github = github;
        this.grants = grants;
    }
    
    public ConfigData() {
        this(new DiscordInfo(null), new GithubInfo(0, null, false), new ArrayList<>());
    }
}
