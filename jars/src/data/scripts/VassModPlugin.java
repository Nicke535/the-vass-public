package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.VassRandomEncounterPlugin;
import data.scripts.campaign.VassSafetyOverridesCrewLossPlugin;
import data.scripts.campaign.VassSectorSetupScript;
import data.scripts.campaign.barEvents.VassPerturbaGetShipSubmarketEventCreator;
import data.scripts.campaign.barEvents.VassPerturbaWeaponContractEventCreator;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingEventCreator;
import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;

import static data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_IN_COMBAT_SCORE_HANDLER;


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

        //Checks for ShaderLib, and starts light/texture data if we have it
        hasShaderLib = Global.getSettings().getModManager().isModEnabled("shaderLib");
        if (hasShaderLib) {
            ShaderLib.init();
            //LightData.readLightDataCSV("data/lights/vass_light_data.csv");
            TextureData.readTextureDataCSV("data/lights/vass_texture_data.csv");
        }
    }

    //Adds all our campaign plugins on new game start
    @Override
    public void onNewGame() {
        SectorAPI sector = Global.getSector();
        VassSectorSetupScript.runSetup(sector);
    }

    //Ensure we have our bar event managers added on any game we load
    //Also add all scripts we need to add
    @Override
    public void onGameLoad(boolean newGame) {
        SectorAPI sector = Global.getSector();

        if (!sector.hasScript(VassSafetyOverridesCrewLossPlugin.class)) {
            sector.addScript(new VassSafetyOverridesCrewLossPlugin());
        }
        if (!sector.hasScript(VassFamilyTrackerPlugin.class)) {
            sector.addScript(new VassFamilyTrackerPlugin());
        }
        if (!sector.hasScript(VassRandomEncounterPlugin.class)) {
            sector.addScript(new VassRandomEncounterPlugin());
        }

        addBarEvents();
    }

    //This is just a safety precaution: this data shouldn't be saved, but I've already made one oversight
    // in that regard so now I'm covering all my bases instead of relying on my coding to work
    @Override
    public void beforeGameSave() {
        super.beforeGameSave();
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_KEY_PROTOTYPE_IN_COMBAT_SCORE_HANDLER);
    }

    private void addBarEvents() {
        BarEventManager bar = BarEventManager.getInstance();
        if (!bar.hasEventCreator(VassPerturbaWeaponContractEventCreator.class)) {
            bar.addEventCreator(new VassPerturbaWeaponContractEventCreator());
        }
        if (!bar.hasEventCreator(VassPerturbaWeaponTestingEventCreator.class)) {
            bar.addEventCreator(new VassPerturbaWeaponTestingEventCreator());
        }
        if (!bar.hasEventCreator(VassPerturbaGetShipSubmarketEventCreator.class)) {
            bar.addEventCreator(new VassPerturbaGetShipSubmarketEventCreator());
        }
    }
}
