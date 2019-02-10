package net.minecraftforge.actuarius.config;

import java.util.ArrayList;
import java.util.List;

public class PermissionEntry {
    
    public final long id;
    public final List<String> repos;
    
    public PermissionEntry(long id, List<String> repos) {
        this.id = id;
        this.repos = repos;
    }
    
    public PermissionEntry() {
        this(0, new ArrayList<>());
    }
}
