package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassTimeDistortionProjScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class VassTizonaScript extends VassBasePerturbaMissileScript {
    @Override
    protected void applyEffectOnProjectile(CombatEngineAPI engine, DamagingProjectileAPI proj) {
        //Add a new plugin that keeps track of the projectile
        engine.addPlugin(new VassTimeDistortionProjScript(proj, MathUtils.getRandomNumberInRange(0.6f, 1.65f), "vass_tizona_detonation"));

        //Re-orient the projectile slightly for a more spread-out look
        proj.getLocation().set(MathUtils.getRandomPointInCircle(proj.getLocation(), 5f));

        //Randomly decrease the projectile's lifetime slightly (if it's a missile)
        if (proj instanceof MissileAPI) {
            ((MissileAPI) proj).setFlightTime(MathUtils.getRandomNumberInRange(0f, 0.2f));
        }
    }
}