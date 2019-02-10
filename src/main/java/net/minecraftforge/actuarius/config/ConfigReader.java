package net.minecraftforge.actuarius.config;

import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.FileConfig;

public class ConfigReader {
    
    private final FileConfig config;
    private final ObjectConverter converter = new ObjectConverter();
    
    private ConfigData data;
    
    public ConfigReader() {
        config = FileConfig.builder("actuarius.toml").concurrent().defaultResource("/default_config.toml").build();
    }
    
    public void save() {
        if (data != null) {
            converter.toConfig(data, config);
            config.save();
        }
    }

    public void load() {
        config.load();
        data = converter.toObject(config, ConfigData::new);
    }
    
    public ConfigData getData() {
        return data;
    }
}
