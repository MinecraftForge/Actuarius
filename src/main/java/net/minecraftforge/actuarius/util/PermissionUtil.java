package net.minecraftforge.actuarius.util;

import java.util.List;

import com.electronwill.nightconfig.core.Config;

import discord4j.core.object.entity.Member;
import discord4j.core.object.util.Snowflake;
import net.minecraftforge.actuarius.Main;
import net.minecraftforge.actuarius.config.PermissionEntry;
import reactor.util.annotation.Nullable;

public class PermissionUtil {
    
    public static synchronized boolean canAccess(Member member, String repo) {
        return canAccess(member.getId(), repo) || member.getRoleIds().stream().anyMatch(id -> canAccess(id, repo));
    }
    
    public static synchronized boolean canAccess(Snowflake id, String repo) {
        List<String> repos = getRepos(id);
        return repos != null && (repos.size() == 0 || repos.contains(repo));
    }
    
    private static @Nullable List<String> getRepos(Snowflake id) {
        
        List<PermissionEntry> grants = Main.cfg.grants;
        
        for (PermissionEntry grant : grants) {
            if (grant.id == id.asLong()) {
                return grant.repos;
            }
        }
        return null;
    }

}
