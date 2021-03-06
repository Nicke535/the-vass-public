//Credit goes to Psiyon for his firecontrol AI script which this is loosely based on
package data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

public class VassTemporalRecallAI implements ShipSystemAIScript {

    private ShipSystemAPI system;
    private ShipAPI ship;

    //Sets an interval for once every 1-1.5 seconds. (meaning the code will only run once this interval has elapsed, not every frame)
    private final IntervalUtil tracker = new IntervalUtil(0.25f, 0.35f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        //Once the interval has elapsed...
        if (tracker.intervalElapsed()) {
            //If our spetum launcher is out of ammo, OR we're retreating back to our carrier, activate the system
            for (WeaponAPI weapon : ship.getAllWeapons()) {
                if (weapon.getAmmo() == 0 && weapon.getId().contains("vass_excalibur_launcher") && ship.getWing().getSourceShip() != null) {
                    activateSystem();
                }
            }
            if (ship.getWing() != null) {
                if (ship.getWing().getReturnData(ship) != null) {
                    activateSystem();
                }
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