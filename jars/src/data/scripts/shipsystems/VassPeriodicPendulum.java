package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import data.scripts.VassModPlugin;
import data.scripts.util.MagicRender;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.dark.shaders.post.PostProcessShader;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

/**
 * Funky flavor of time mult incoming!
 * @author Nicke535
 * @deprecated
 */
public class VassPeriodicPendulum extends BaseShipSystemScript {
    //The maximum and minimum time mult the system can achieve
    public static final float TIME_MULT_MAX = 20f;
    public static final float TIME_MULT_MIN = 0.2f;

    //The period time of the time pendulum, in "balanced time" [realtime or ship time, whichever has smallest realtime impact]
    public static final float TIME_PENDULUM_PERIOD = 3.5f;

    //Jitter color for the system, in "min" and "max" mode
    public static final Color JITTER_COLOR_MAX = new Color(255, 26, 26,55);
    public static final Color JITTER_UNDER_COLOR_MAX = new Color(255, 0, 0,155);
    public static final Color JITTER_COLOR_MIN = new Color(26, 26, 255,55);
    public static final Color JITTER_UNDER_COLOR_MIN = new Color(0, 0, 255,155);

    private float timer = MathUtils.getRandomNumberInRange(0f, 99f);

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //Calculates pendulum state for this frame
        float pendulumState = (float)Math.sin((timer*Math.PI*2f)/TIME_PENDULUM_PERIOD);
        pendulumState = (float)Math.sqrt(Math.abs(pendulumState))*Math.signum(pendulumState);
        float colorState =(pendulumState + 1f)/2f;
		
		//Jitter-based code
        float jitterLevel = effectLevel;
        float jitterRangeBonus = 0;
        float maxRangeBonus = 10f;
        jitterRangeBonus = jitterLevel * maxRangeBonus;

        Color jitterColor = Misc.interpolateColor(JITTER_COLOR_MIN, JITTER_COLOR_MAX, colorState);
        Color jitterColorUnder = Misc.interpolateColor(JITTER_UNDER_COLOR_MIN, JITTER_UNDER_COLOR_MAX, colorState);
        ship.setJitter(this, jitterColor, jitterLevel, (int)Math.ceil(4 * jitterLevel), 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, jitterColorUnder, jitterLevel, (int)Math.ceil(20 * jitterLevel), 0f, 5f + jitterRangeBonus);

		//Adjusts time mult
		float timeMultThisFrame = 1f + (TIME_MULT_MAX - 1f)*pendulumState*effectLevel;
		if (pendulumState < 0f) {
		    timeMultThisFrame = 1f + (TIME_MULT_MIN - 1f)*(pendulumState*-1f)*effectLevel;
        }
        stats.getTimeMult().modifyMult(id, timeMultThisFrame);
        if (player) {
            Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / timeMultThisFrame);
        } else {
            Global.getCombatEngine().getTimeMult().unmodify(id);
        }
		
		//Changes engine color
        ship.getEngineController().fadeToOtherColor(this, jitterColor, new Color(0,0,0,0), effectLevel, 1.0f);
        ship.getEngineController().extendFlame(this, -0.25f, -0.25f, -0.25f);

        //Reduces beam damage
        stats.getBeamWeaponFluxCostMult().modifyMult(id, 1f/timeMultThisFrame);
        stats.getBeamWeaponDamageMult().modifyMult(id, 1f/timeMultThisFrame);

        //Increases our timer, so next frame has different stats
        float amount = Global.getCombatEngine().getElapsedInLastFrame();
        /*if (timeMultThisFrame > 1f) {
            amount *= timeMultThisFrame;
        }*/
        timer += amount;
    }

    //Resets all our values to pre-activation state
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
		
        Global.getCombatEngine().getTimeMult().unmodify(id);
        stats.getTimeMult().unmodify(id);

		stats.getBeamWeaponFluxCostMult().unmodify(id);
		stats.getBeamWeaponDamageMult().unmodify(id);
    }

    //Shows a tooltip in the HUD
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Time Pulses Active", false);
        }
        return null;
    }
}