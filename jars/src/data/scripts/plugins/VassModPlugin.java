package data.scripts.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;


public class VassModPlugin extends BaseModPlugin {

    public static boolean hasShaderLib = false;

    //Courtesy of Tartiflette: small piece of code which informs the user that LazyLib is necessary should something go wrong
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

        //Check ShaderLib for lights

        hasShaderLib = Global.getSettings().getModManager().isModEnabled("shaderLib");

        if (hasShaderLib) {
            ShaderLib.init();
            LightData.readLightDataCSV("data/lights/vass_light_data.csv");
            TextureData.readTextureDataCSV("data/lights/vass_texture_data.csv");
        }
    }


    @Override
    public void onNewGame() {
        SectorAPI sector = Global.getSector();
    }
}
