package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.shipsystems.VassIsochronalField;
import data.scripts.shipsystems.VassTemporalRetreat;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * Attempts to determine if the ship would benefit more from defense or offense at the moment, and adapts thereafter
 * @author Nicke535
 */
public class VassIsochronalFieldAI implements ShipSystemAIScript {
    //Internal varibles
    private ShipSystemAPI system;
    private ShipAPI ship;

    //Checks quite often for a good activation time
    private final IntervalUtil tracker = new IntervalUtil(0.13f, 0.15f);


    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        //Once the interval has elapsed...
        if (tracker.intervalElapsed() && system.getEffectLevel() <= 0f) {
            //First, check if we're in defensive or offensive mode. This will affect our decisionmaking down the line
            boolean offensiveMode = Global.getCombatEngine().getCustomData().containsKey(VassIsochronalField.OFFENSE_MEM_KEY);

            boolean desiresOffensive = false;

            //TODO: IMPLEMENT

            if (desiresOffensive && !offensiveMode) {
                activateSystem();
            } else if (!desiresOffensive && offensiveMode) {
                activateSystem();
            }
        }
    }

    private void deactivateSystem() {
        if (system.isOn()) {
            ship.useSystem();
        }
    }

    private void activateSystem() {
        if (!system.isOn()) {
            ship.useSystem();
        }
    }
}
