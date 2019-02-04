package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

public class VassCloneFlarePlugin extends BaseEveryFrameCombatPlugin {

    private static IntervalUtil interval = new IntervalUtil(0.05f, 0.06f);

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null){return;}

        interval.advance(amount);

        //Only run the code if our interval has elapsed
        if (interval.intervalElapsed()) {
            //Iterate through all missiles on the map
            for (MissileAPI potentialFlare : Global.getCombatEngine().getMissiles()) {
                //If the missile is a not a Clone Flare, or it no longer has a source ship, ignore it
                if (!potentialFlare.getProjectileSpecId().contains("vass_clone_flare") || potentialFlare.getSource() != null) {
                    continue;
                }

                Vector2f pos = potentialFlare.getLocation();
                float angle = potentialFlare.getSource().getFacing();
                //Create a random color: it is a Multa system, after all
                Color randomColor = new Color(MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), 0.8f);

                //The magic happens here: render the sprite at the correct location, with identical facing to the source ship
                VassSpriteRenderManager.battlespaceRender(Global.getSettings().getSprite("vass_fx", "yataghan_clone"), pos, new Vector2f(0f, 0f), new Vector2f(59f, 50f),
                                                          new Vector2f(0f, 0f), angle, 0f, randomColor, true, 0.01f, 0.05f, 0.05f);
            }
        }
    }
}
