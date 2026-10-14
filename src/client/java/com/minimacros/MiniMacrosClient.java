package com.minimacros;

import com.minimacros.config.MacroStorage;
import com.minimacros.gui.MacroEditorScreen;
import com.minimacros.runtime.MacroRunner;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiniMacrosClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Mini Macros");

	/** Unbound by default — the editor is normally reached through Mod Menu. */
	private static final KeyMapping OPEN_EDITOR = new KeyMapping(
			"key.mini-macros.open_editor",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			KeyMapping.Category.MISC);

	private static final KeyMapping PANIC = new KeyMapping(
			"key.mini-macros.panic",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_RSHIFT,
			KeyMapping.Category.MISC);

	@Override
	public void onInitializeClient() {
		MacroStorage.load();

		KeyMappingHelper.registerKeyMapping(OPEN_EDITOR);
		KeyMappingHelper.registerKeyMapping(PANIC);

		ClientTickEvents.END_CLIENT_TICK.register(MiniMacrosClient::onEndClientTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> MacroRunner.get().reset());
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> MacroRunner.get().reset());

		LOGGER.info("Mini Macros ready with {} macro(s)", MacroStorage.macros().size());
	}

	private static void onEndClientTick(Minecraft minecraft) {
		boolean panicPressed = false;
		while (PANIC.consumeClick()) {
			panicPressed = true;
			MacroRunner.get().stopAll();
		}
		while (OPEN_EDITOR.consumeClick()) {
			minecraft.setScreenAndShow(new MacroEditorScreen(minecraft.gui.screen()));
		}

		MacroRunner.get().onEndClientTick(minecraft, !panicPressed && !PANIC.isDown());
	}
}
