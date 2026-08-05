package cn.mythicland.mythicmobsaddon.web;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.web.EmbeddedHttpServer;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

/** Lifecycle wrapper for the MythicMobsAddon web service. */
public final class MythicMobsAddonWebServer implements AutoCloseable {

    private final JavaPlugin plugin;
    private final LibApi lib;
    private EmbeddedHttpServer server;
    private String bindAddress;
    private int port;

    public MythicMobsAddonWebServer(JavaPlugin plugin, LibApi lib) {
        this.plugin = plugin;
        this.lib = lib;
    }

    public void start(String bindAddress, int port, String token, MythicItemService service)
            throws IOException {
        close();
        this.bindAddress = bindAddress;
        this.port = port;
        server = lib.webService().start(bindAddress, port,
                new MythicMobsAddonWebHandler(plugin, lib, service, token));
    }

    public boolean isRunning() {
        return server != null;
    }

    public String displayAddress() {
        return "http://" + bindAddress + ":" + (server == null ? port : server.port());
    }

    @Override
    public void close() {
        if (server != null) server.close();
        server = null;
    }
}
