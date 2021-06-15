package co.runed.kawarau;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class Config
{
    Configuration config;

    public String databaseUrl;
    public String databasePort;
    public String databaseUsername;
    public String databasePassword;
    public String databaseName;

    public String redisHost = "localhost";
    public int redisPort = 6379;
    public String redisPassword;

    public Config(Plugin plugin)
    {
        try
        {
            if (!plugin.getDataFolder().exists())
            {
                plugin.getDataFolder().mkdir();
            }

            File file = new File(plugin.getDataFolder(), "config.yml");

            if (!file.exists())
            {
                try (InputStream in = plugin.getResourceAsStream("config.yml"))
                {
                    Files.copy(in, file.toPath());
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }

            this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        }
        catch (IOException e)
        {
            this.config = new Configuration();
            e.printStackTrace();
        }

        Configuration database = this.config.getSection("database");
        this.databaseUrl = database.getString("url", "localhost");
        this.databasePort = database.getString("port", "27071");
        this.databaseUsername = database.getString("username", "admin");
        this.databasePassword = database.getString("password", "password");
        this.databaseName = database.getString("database", "bolster");

        Configuration redis = this.config.getSection("redis");
        this.redisHost = redis.getString("host", redisHost);
        this.redisPort = redis.getInt("port", redisPort);
        this.redisPassword = redis.getString("password", redisPassword);
    }
}
