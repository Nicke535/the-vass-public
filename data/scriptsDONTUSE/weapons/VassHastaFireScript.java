package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.*;
import java.awt.*;

public class VassHastaFireScript implements EveryFrameWeaponEffectPlugin {

    private boolean hasFiredThisCharge = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // Don't bother with any checks if the game is paused
        if (engine.isPaused()) {
            return;
        }

        ShipAPI ship = weapon.getShip();
		//...or if this ship is not the Makhaira
		if (!ship.getHullSpec().getHullId().contains("vass_makhaira")) {
			return;
		}
		
		if (weapon.getChargeLevel() <= 0.1f) {
			hasFiredThisCharge = false;
		} else if (weapon.getChargeLevel() >= 0.9f && !hasFiredThisCharge) {
			hasFiredThisCharge = true;
			ship.getMutableStats().getDynamic().getStat("VassAhlspiessWaitingAmmo").modifyFlat("VassAhlspiessWaitingAmmoID",ship.getMutableStats().getDynamic().getStat("VassAhlspiessWaitingAmmo").getModifiedValue()+1);
		}
    }
}