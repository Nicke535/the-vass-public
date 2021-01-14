package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.shipsystems.VassIsochronalField;
import data.scripts.shipsystems.VassTemporalRetreat;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * Attempts to determine if the ship would benefit more from defense or offense at the moment, and adapts thereafter
 * @author Nicke535
 */
public class VassIsochronalFieldAI implements ShipSystemAIScript {
    private static final float HIGH_FLUX_THRESHOLD = 0.8f;

    //Internal varibles
    private ShipSystemAPI system;
    private ShipAPI ship;

    private final IntervalUtil tracker = new IntervalUtil(0.7f, 1.5f);


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

            boolean desiresOffensive = true;

            //First off: are we running away, or at too high flux? In that case, just directly switch to defensive
            if (ship.getFluxLevel() > HIGH_FLUX_THRESHOLD
                    || ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)
                    || ship.isRetreating()
                    || ship.isDirectRetreat()) {
                desiresOffensive = false;
            }

            //Second: are a majority of our applicable weapons disabled? If so, also go on the defensive: we get more mileage out of it
            int weaponsDisabled = 0;
            for (WeaponAPI wep : ship.getDisabledWeapons()) {
                if (wep.getSlot().isHardpoint()) {
                    weaponsDisabled++;
                }
            }
            if (weaponsDisabled >= 2) {
                desiresOffensive = false;
            }

            //Third, are there any targets nearby? If not, go on the defensive
            //      This is the heaviest check, so we don't even bother checking if we know we should be on the defensive
            if (desiresOffensive) {
                ShipAPI enemy = AIUtils.getNearestEnemy(ship);
                if (enemy == null || MathUtils.getDistance(enemy, ship) > getLongestWeaponRange()*1.25f) {
                    desiresOffensive = false;
                }
            }

            if (desiresOffensive && !offensiveMode) {
                activateSystem();
            } else if (!desiresOffensive && offensiveMode) {
                activateSystem();
            }
        }
    }

    private void activateSystem() {
        if (!system.isOn()) {
            ship.useSystem();
        }
    }

    //Shorthand for getting the longest applicable weapon range on our ship
    private float getLongestWeaponRange() {
        float maxRange = 0f;
        for (WeaponAPI wep : ship.getAllWeapons()) {
            if (!wep.getSlot().isHardpoint()) {
                continue;
            }

            if (maxRange < wep.getRange()) {
                maxRange = wep.getRange();
            }
        }
        return maxRange;
    }
}
