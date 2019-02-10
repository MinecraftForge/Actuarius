package net.minecraftforge.actuarius.config;

import com.electronwill.nightconfig.core.conversion.Path;

public class GithubInfo {
    
    @Path("app_id")
    public final int appId;

    @Path("private_key")
    public String privateKey;

    @Path("default_installation")
    public final String defaultInstallation;

    @Path("force_default")
    public final boolean forceDefault;

    public GithubInfo(int appId, String defaultInstallation, boolean forceDefault) {
        this.appId = appId;
        this.defaultInstallation = defaultInstallation;
        this.forceDefault = forceDefault;
    }
}
