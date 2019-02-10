package net.minecraftforge.actuarius.commands;

import java.io.IOException;

import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHRepository;

import net.minecraftforge.actuarius.util.GithubUtil;
import reactor.core.publisher.Mono;

public class CommandRepoInfo implements Command {

    @Override
    public Mono<?> invoke(Context ctx) throws CommandException {
        String[] args = ctx.getArgs();
        String owner = "MinecraftForge", repo;
        if (args.length >= 2) {
            owner = args[0];
            repo = args[1];
        } else if (args.length == 1) {
            repo = args[0];
        } else {
            return ctx.error("Wrong number of arguments.");
        }
        
        try {
            
            GHPerson org;
            try {
                org = GithubUtil.getUnauthorizedClient().getOrganization(owner);
            } catch (IOException e) {
                org = GithubUtil.getUnauthorizedClient().getUser(owner);
            }
            
            if (org == null) {
                return ctx.error("No such user.");
            }
            GHRepository repository = org.getRepository(repo);
            if (repository == null) {
                return ctx.error("No such repository.");
            }
            
            return ctx.reply(embed -> {
                embed.setTitle(repository.getOwnerName() + "/" + repository.getName());
                String desc = repository.getDescription();
                if (desc != null) {
                    embed.setDescription(repository.getDescription());
                }
                embed.addField("Open Issues/PRs", String.valueOf(repository.getOpenIssueCount()), true);
                embed.addField("Stars", String.valueOf(repository.getStargazersCount()), true);
            });
        } catch (IOException e) {
            e.printStackTrace();
            return ctx.error("Error getting repository info.", e);
        }
    }

    @Override
    public String description() {
        return "Get information on a repository.";
    }
    
    @Override
    public String usage() {
        return "[owner] <repository>";
    }
}
