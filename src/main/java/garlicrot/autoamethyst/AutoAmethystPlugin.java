package garlicrot.autoamethyst;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

import java.util.logging.Logger;

/** RusherHack plugin entry point. */
public final class AutoAmethystPlugin extends Plugin {
    private static final Logger LOGGER = Logger.getLogger(AutoAmethystPlugin.class.getName());

    @Override
    public void onLoad() {
        RusherHackAPI.getModuleManager().registerFeature(AutoAmethystModule.getInstance());
        LOGGER.info("AutoAmethyst registered");
    }

    @Override
    public void onUnload() {
        AutoAmethystModule module = AutoAmethystModule.getInstance();
        if (module.isToggled()) module.toggle();
        LOGGER.info("AutoAmethyst unloaded");
    }
}
