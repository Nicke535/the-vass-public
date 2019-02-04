package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.*;
import java.awt.*;

public class VassAhlspiessReloadScript implements EveryFrameWeaponEffectPlugin {

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // Don't bother with any checks if the game is paused
        if (engine.isPaused()) {
            return;
        }

        ShipAPI ship = weapon.getShip();
		
		ship.getMutableStats().getDynamic().getStat("VassAhlspiessWaitingAmmo").modifyFlat("VassAhlspiessWaitingAmmoNullifierID",-1);
        weapon.setAmmo((int)(weapon.getAmmo() + ship.getMutableStats().getDynamic().getStat("VassAhlspiessWaitingAmmo").getModifiedValue()));
		ship.getMutableStats().getDynamic().getStat("VassAhlspiessWaitingAmmo").modifyFlat("VassAhlspiessWaitingAmmoID",0);
    }
}