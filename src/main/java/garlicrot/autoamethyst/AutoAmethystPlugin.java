package garlicrot.autoamethyst;

import org.rusherhack.client.api.RusherHackAPI;

import java.util.logging.Logger;

public class AutoAmethystPlugin extends org.rusherhack.client.api.plugin.Plugin {
    private static final Logger LOGGER = Logger.getLogger(AutoAmethystPlugin.class.getName());

    @Override
    public void onLoad() {
        RusherHackAPI.getModuleManager().registerFeature(AutoAmethystModule.getInstance());
        LOGGER.info("AutoAmethyst loaded and module registered!");
    }

    @Override
    public void onUnload() {
        AutoAmethystModule module = AutoAmethystModule.getInstance();
        if (module.isToggled()) {
            module.toggle();
            LOGGER.info("AutoAmethyst module disabled during unload");
        }
        LOGGER.info("AutoAmethyst unloaded!");
    }
}
