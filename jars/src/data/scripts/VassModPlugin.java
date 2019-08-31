package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.VassSafetyOverridesCrewLossPlugin;


public class VassModPlugin extends BaseModPlugin {

    public static boolean hasShaderLib = false;

    //Detects any non-loaded required mods, and throws an error
    @Override
    public void onApplicationLoad() throws ClassNotFoundException {
        try {
            Global.getSettings().getScriptClassLoader().loadClass("org.lazywizard.lazylib.ModUtils");
        } catch (ClassNotFoundException ex) {
            String message = System.lineSeparator()
                    + System.lineSeparator() + "LazyLib is required to run at least one of the mods you have installed."
                    + System.lineSeparator() + System.lineSeparator()
                    + "You can download LazyLib at http://fractalsoftworks.com/forum/index.php?topic=5444"
                    + System.lineSeparator();
            throw new ClassNotFoundException(message);
        }
        try {
            Global.getSettings().getScriptClassLoader().loadClass("data.scripts.Magic_modPlugin");
        } catch (ClassNotFoundException ex) {
            String message = System.lineSeparator()
                    + System.lineSeparator() + "MagicLib is required to run at least one of the mods you have installed."
                    + System.lineSeparator() + System.lineSeparator()
                    + "You can download MagicLib at http://fractalsoftworks.com/forum/index.php?topic=13718.0"
                    + System.lineSeparator();
            throw new ClassNotFoundException(message);
        }

        //Checks for ShaderLib
        hasShaderLib = Global.getSettings().getModManager().isModEnabled("shaderLib");
    }

    //Adds all our campaign plugins on new game start
    @Override
    public void onNewGame() {
        SectorAPI sector = Global.getSector();
        sector.addScript(new VassSafetyOverridesCrewLossPlugin());
        sector.addScript(new VassFamilyTrackerPlugin());
    }
}
