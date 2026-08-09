package com.minimacros;

import com.minimacros.gui.MacroEditorScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Puts the editor behind Mods → Mini Macros → Config. */
public class MiniMacrosModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return MacroEditorScreen::new;
	}
}
