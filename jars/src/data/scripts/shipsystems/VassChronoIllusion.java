package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class VassChronoIllusion extends BaseShipSystemScript {
    //How long the illusions fired by the system are allowed to exist, at most
    static final float MAX_ILLUSION_DURATION = 8f;

    //How long visual illusion copies which are spawned by each illusion are allowed to live
    static final float ILLUSION_CLONE_DURATION = 0.45f;

    //How quickly visual illusion copies are spawned by each illusion
    static final float ILLUSION_CLONE_DELAY = 0.1f;


    List<ChronoIllusionTracker> activeTrackers = new ArrayList<ChronoIllusionTracker>();

    ShipAPI ship = null;

    private IntervalUtil fireInterval = new IntervalUtil(1.15f, 1.35f);
    private float timer = 0f;
    private boolean firstRun = true;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //First time we activate, fire off two clones instantly
        if (firstRun) {
            firstRun = false;
            for (int i = 0; i < 2; i++) {
                DamagingProjectileAPI proj = (DamagingProjectileAPI)Global.getCombatEngine().spawnProjectile(ship, null, "vass_illusion_flarelauncher",
                        ship.getLocation(), MathUtils.getRandomNumberInRange(0f, 360f), ship.getVelocity());
                ChronoIllusionTracker plugin = new ChronoIllusionTracker(this, proj);
                Global.getCombatEngine().addPlugin(plugin);
                activeTrackers.add(plugin);
            }
        }

        //Fires off projectiles at random intervals
        float amount = Global.getCombatEngine().getElapsedInLastFrame() * ship.getMutableStats().getTimeMult().modified;
        fireInterval.advance(amount);
        if (fireInterval.intervalElapsed()) {
            DamagingProjectileAPI proj = (DamagingProjectileAPI)Global.getCombatEngine().spawnProjectile(ship, null, "vass_illusion_flarelauncher",
                    ship.getLocation(), MathUtils.getRandomNumberInRange(0f, 360f), ship.getVelocity());
            ChronoIllusionTracker plugin = new ChronoIllusionTracker(this, proj);
            Global.getCombatEngine().addPlugin(plugin);
            activeTrackers.add(plugin);
        }

        //Also spawns the visual effects of the clones on the ship itself
        ship.setExtraAlphaMult(1f - effectLevel);
        timer += amount;
        if (timer > ILLUSION_CLONE_DELAY*1.5f) {
            timer -= ILLUSION_CLONE_DELAY*1.5f;
            SpriteAPI spriteToRender = Global.getSettings().getSprite("vass_fx", "katzbalger_illusion");
            MagicRender.battlespace(spriteToRender, new Vector2f(ship.getLocation()), Misc.ZERO,
                    new Vector2f(50f, 49f), Misc.ZERO, ship.getFacing() - 90f, 0f,
                    Misc.interpolateColor(VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 0.25f), new Color(1f, 1f, 1f, 0.25f), 0.3f),
                    true,ILLUSION_CLONE_DURATION*0.1f, ILLUSION_CLONE_DURATION*0.1f, ILLUSION_CLONE_DURATION*0.8f,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER);
        }

        //And cheat a bit: make its shield invulnerable
        ship.getMutableStats().getShieldDamageTakenMult().modifyMult(id, 1f - (1f * effectLevel));
    }


    public void unapply(MutableShipStatsAPI stats, String id) {
        ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        firstRun = true;

        //Reset opacity and shield damage
        ship.setExtraAlphaMult(1f);
        ship.getMutableStats().getShieldDamageTakenMult().unmodify(id);

        //And tell our plugins to just stop
        activeTrackers.clear();
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("I'm over here now!", false);
        }
        return null;
    }

    //Plugin that tracks the illusion status of an individual projectile
    private class ChronoIllusionTracker extends BaseEveryFrameCombatPlugin {
        VassChronoIllusion parentScript;
        DamagingProjectileAPI proj;

        final float TURN_SPEED = 90f;

        float timer = MathUtils.getRandomNumberInRange(0f, ILLUSION_CLONE_DELAY);
        float fireTimer = MathUtils.getRandomNumberInRange(0f, 0.4f);

        float lifetime = 0f;
        float currentAngle;

        ChronoIllusionTracker(VassChronoIllusion parentScript, DamagingProjectileAPI proj) {
            this.parentScript = parentScript;
            this.proj = proj;
            if (parentScript.ship != null) {
                this.currentAngle = parentScript.ship.getFacing();
            } else {
                currentAngle = proj.getFacing();
            }
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (Global.getCombatEngine().isPaused()) {
                amount = 0f;
            }

            //Remove ourselves if we are no longer valid
            if (!Global.getCombatEngine().isEntityInPlay(proj) || parentScript.ship == null) {
                parentScript.activeTrackers.remove(this);
                Global.getCombatEngine().removePlugin(this);
                return;
            } else if (lifetime > MAX_ILLUSION_DURATION || !parentScript.activeTrackers.contains(this)) {
                Global.getCombatEngine().removeEntity(proj);
                parentScript.activeTrackers.remove(this);
                Global.getCombatEngine().removePlugin(this);
                return;
            }

            //Turn towards our parent ship's ship target at all times: if we have none, turn towards the paren't ship's facing
            float targetAngle = parentScript.ship.getFacing();
            if (parentScript.ship.getShipTarget() != null) {
                targetAngle = VectorUtils.getAngle(proj.getLocation(), parentScript.ship.getShipTarget().getLocation());
            }
            float direction = MathUtils.getShortestRotation(currentAngle, targetAngle);
            float directionSign = Math.signum(direction);
            if (amount * TURN_SPEED < Math.abs(direction)) {
                currentAngle = targetAngle;
            } else {
                currentAngle += amount * TURN_SPEED * directionSign;
            }

            //Occasionally fire a shot from a (fake) Dyrnwyn
            fireTimer += amount;
            if (fireTimer > 0.4f) {
                fireTimer -= 0.4f;
                DamagingProjectileAPI fakeProj = (DamagingProjectileAPI)Global.getCombatEngine().spawnProjectile(parentScript.ship, null, "vass_dyrnwyn_fake",
                        proj.getLocation(), targetAngle + MathUtils.getRandomNumberInRange(-10f, 10f), proj.getVelocity());
                Global.getSoundPlayer().playSound("vass_dyrnwyn_fire", MathUtils.getRandomNumberInRange(0.95f, 1.05f), MathUtils.getRandomNumberInRange(0.3f, 0.7f), proj.getLocation(), Misc.ZERO);
            }

            //Actually render our clones
            timer += amount;
            lifetime += amount;
            if (timer > ILLUSION_CLONE_DELAY) {
                timer -= ILLUSION_CLONE_DELAY;
                SpriteAPI spriteToRender = Global.getSettings().getSprite("vass_fx", "katzbalger_illusion");
                MagicRender.battlespace(spriteToRender, new Vector2f(proj.getLocation()), Misc.ZERO,
                        new Vector2f(50f, 49f), Misc.ZERO, targetAngle - 90f, 0f,
                        Misc.interpolateColor(VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 0.15f), new Color(1f, 1f, 1f, 0.15f), 0.3f),
                        true,ILLUSION_CLONE_DURATION*0.1f, ILLUSION_CLONE_DURATION*0.1f, ILLUSION_CLONE_DURATION*0.8f,
                        CombatEngineLayers.BELOW_INDICATORS_LAYER);
            }
        }
    }
}