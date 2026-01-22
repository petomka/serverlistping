package de.hytale_server.serverlistping;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.util.logging.Level;

public class ServerlistPing extends JavaPlugin {

    private final Config<StatusConfig> statusConfig = withConfig(StatusConfig.CODEC);
    private StatusQueryServer queryServer;

    public ServerlistPing(@NonNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("ServerStatus plugin is setting up");
        this.statusConfig.save();
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("ServerStatus plugin is starting");

        queryServer = new StatusQueryServer(getLogger(), statusConfig.get());

        try {
            queryServer.start();
            getLogger().at(Level.INFO).log("Status query service available on port " + statusConfig.get().getPort());
        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("Failed to start status query server", e);
        }
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("ServerStatus plugin is shutting down");

        if (queryServer != null) {
            queryServer.stop();
        }
    }

}
