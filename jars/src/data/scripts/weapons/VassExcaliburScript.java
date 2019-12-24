package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassTimeDistortionProjScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.List;

public class VassExcaliburScript extends VassBasePerturbaMissileScript {
    @Override
    protected void applyEffectOnProjectile(CombatEngineAPI engine, DamagingProjectileAPI proj) {
        //Makes projectiles slightly cooler on fade-out
        if (proj instanceof MissileAPI) {
            ((MissileAPI) proj).setFlightTime(MathUtils.getRandomNumberInRange(0f, 0.2f));
        }

        //Add a new plugin that keeps track of the projectile
        engine.addPlugin(new VassTimeDistortionProjScript(proj, MathUtils.getRandomNumberInRange(0.5f, 1.8f), "vass_excalibur_detonation", 0.85f));
    }

    //The Excalibur can't regen ammo, as it's on a bomber
    @Override
    protected float ammoRegenMult() {
        return 0f;
    }
}