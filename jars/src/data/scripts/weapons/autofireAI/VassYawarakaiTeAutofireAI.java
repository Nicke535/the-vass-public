package data.scripts.weapons.autofireAI;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.weapons.VassYawarakaiTeScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VassYawarakaiTeAutofireAI implements AutofireAIPlugin {

    private WeaponAPI weapon;
    private IntervalUtil timer = new IntervalUtil(0.02f, 0.03f);
    private boolean needImmediateReset = false;
    private boolean shouldFire = false;

    public VassYawarakaiTeAutofireAI(WeaponAPI weapon) {
        this.weapon = weapon;
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused()) {
            amount = 0f;
        }
        timer.advance(amount);
        if (timer.intervalElapsed() || needImmediateReset) {
            needImmediateReset = false;

            // Verifies so that the yawarakai te script is actually available on the weapon. If not, this is a bugged situation! Don't fire at all.
            if (!(weapon.getEffectPlugin() instanceof VassYawarakaiTeScript)) {
                shouldFire = false;
                return;
            }

            VassYawarakaiTeScript plugin = (VassYawarakaiTeScript)weapon.getEffectPlugin();
            Map<MissileAPI, Float> knownMissiles = new HashMap<>();

            // If our plugin has started the missile status map, save any missiles listed in it
            if (plugin.missileStatusMap != null) {
                knownMissiles.putAll(plugin.missileStatusMap);
            }

            boolean ignoresFlares = false;
            if (weapon.getShip().getMutableStats().getDynamic().getMod(Stats.PD_IGNORES_FLARES) != null &&
                    weapon.getShip().getMutableStats().getDynamic().getMod(Stats.PD_IGNORES_FLARES).computeEffective(0f) >= 1f) {
                ignoresFlares = true;
            }

            for (MissileAPI msl : CombatUtils.getMissilesWithinRange(weapon.getLocation(), weapon.getRange())) {
                //Ignore friendlies
                if (msl.getOwner() == weapon.getShip().getOwner()) {
                    continue;
                }
                //Ignore flares, if applicable
                if (ignoresFlares && msl.isFlare()) {
                    continue;
                }
                // Only care about missiles in arc
                if (weapon.distanceFromArc(msl.getLocation()) > 0f) {
                    continue;
                }
                //Ignore targets that are already flamed out by us or another yawarakai-te adjacent weapon
                if (knownMissiles.get(msl) != null && knownMissiles.get(msl) > msl.getHitpoints()) {
                    continue;
                }

                //We have now found a non-ignored missile! We should fire on autofire
                shouldFire = true;
                return;
            }

            //No missiles were found: don't fire on autofire
            shouldFire = false;
        }
    }

    @Override
    public boolean shouldFire() {
        return shouldFire;
    }

    @Override
    public void forceOff() {
        needImmediateReset = true;
        advance(0f);
    }

    @Override
    public Vector2f getTarget() {
        return null;
    }

    @Override
    public ShipAPI getTargetShip() {
        return null;
    }

    @Override
    public WeaponAPI getWeapon() {
        return weapon;
    }

    @Override
    public MissileAPI getTargetMissile() {
        return null;
    }
}
