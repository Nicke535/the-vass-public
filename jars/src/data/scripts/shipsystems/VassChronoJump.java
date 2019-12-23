package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.ProjectileSpawnType;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassTimeSplitProjScript;
import data.scripts.utils.VassUtils;
import data.scripts.weapons.VassFragarachScript;
import org.dark.shaders.post.PostProcessShader;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

/**
 * Causes nearby missiles and projectiles to be "shifted" through time, skipping over anything in the way
 * @author Nicke535
 */
public class VassChronoJump extends BaseShipSystemScript {
    //Percieved time mult when charging the system
    public static final float TIME_MULT_PERCIEVED = 0.2f;

    //Max AoE of the system (not including the ship's collision radius)
    public static final float MAX_RANGE = 500f;

    //How far into the future will each projectile be launched?
    public static final float TIME_SKIP_AMOUNT = 1f;

    //The maximum duration the system can be kept active before it is automatically triggered
    public static final float MAX_ACTIVE_DURATION = 0.5f;

    //The minimum duration the system must be kept active before re-activating it
    public static final float MIN_ACTIVE_DURATION = 0.15f;

    // --- Sound --- //
    //Volume increase for the loop sound when close to activation
    private static final float LOOP_MAX_VOLUME_INCREASE = 0.5f;

    //Pitch decrease for the loop sound when close to activation
    private static final float LOOP_MAX_PITCH_DECREASE = 0.4f;

    private boolean triggeredOnce = false;
    private float currentActiveDuration = 0f;
    private boolean hasResetPostProcess = true;
    private boolean hasPlayedSound = false;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        boolean player = false;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (stats.getEntity() instanceof ShipAPI && engine != null) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == engine.getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //Ensure our active duration stops once we're deactivating again
        if (state.equals(State.OUT) || state.equals(State.IDLE) || state.equals(State.COOLDOWN)) {
            currentActiveDuration = 0f;
        }

        //Lose our one-frame invulnerability
        if (ship.getCollisionClass() == CollisionClass.NONE && triggeredOnce) {
            ship.setCollisionClass(CollisionClass.SHIP);
        }

        //While active, but not yet triggering, we have a percieved reduced timeflow and see where each projectile will end up
        //after our trigger (only the player experience this)
        if (!state.equals(State.OUT) && effectLevel > 0f) {
            triggeredOnce = false;
            currentActiveDuration += engine.getElapsedInLastFrame() * ship.getMutableStats().getTimeMult().getModifiedValue();

            if (player) {
                engine.getTimeMult().modifyMult(id, 1f - (1f - TIME_MULT_PERCIEVED) * effectLevel);
                List<DamagingProjectileAPI> allProjs = CombatUtils.getProjectilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE);
                allProjs.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE));
                for (DamagingProjectileAPI proj : allProjs) {
                    spawnFutureIndicator(proj);
                }

                //Post-processing! Can't let II have all the good toys...
                hasResetPostProcess = false;
                PostProcessShader.useExponentialDarkness(false, true);
                PostProcessShader.useExponentialDesaturation(false, true);
                PostProcessShader.setContrast(false, 1f + (0.16f*effectLevel));
            } else {
                if (!hasResetPostProcess) {
                    PostProcessShader.resetDefaults();
                    hasResetPostProcess = true;
                }
                engine.getTimeMult().unmodify(id);
            }

            //Also, play some sound (on loop for AI)!
            if (player) {
                if (!hasPlayedSound) {
                    Global.getSoundPlayer().playSound("vass_chrono_jump_start", 1f, 1f, ship.getLocation(), ship.getVelocity());
                    hasPlayedSound = true;
                }
                //And an extra loop for the player, since our main sound isn't loop-friendly. This scales a bit when the system's about to blow
                if (currentActiveDuration < MAX_ACTIVE_DURATION*0.75f) {
                    Global.getSoundPlayer().playLoop("vass_chrono_jump_loop", ship, 1f, 1f, ship.getLocation(), ship.getVelocity());
                } else {
                    float extraVolumeAndPitchMult = (currentActiveDuration - (MAX_ACTIVE_DURATION*0.75f))/(MAX_ACTIVE_DURATION*0.25f);
                    Global.getSoundPlayer().playLoop("vass_chrono_jump_loop", ship,
                            1f - (LOOP_MAX_PITCH_DECREASE*extraVolumeAndPitchMult), 1f + (LOOP_MAX_VOLUME_INCREASE*extraVolumeAndPitchMult),
                            ship.getLocation(), ship.getVelocity());
                }
            } else {
                Global.getSoundPlayer().playLoop("vass_chrono_jump_start", ship, 1f, 1f, ship.getLocation(), ship.getVelocity());
            }
        }

        //If the system is now being triggered, make you temporarily untargetable, move all projectiles to their new location, and spawn fancy particles
        else if (!triggeredOnce) {
            //First of all, turn you immortal one frame, unmodify the time mult, and register that we've triggered
            ship.setCollisionClass(CollisionClass.NONE);
            engine.getTimeMult().unmodify(id);
            triggeredOnce = true;

            List<DamagingProjectileAPI> allProjs = CombatUtils.getProjectilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE);
            allProjs.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE));
            for (DamagingProjectileAPI proj : allProjs) {
                //Teleport to the future
                Vector2f startPos = new Vector2f(proj.getLocation());
                Vector2f destPos = new Vector2f(startPos.x + proj.getVelocity().x * TIME_SKIP_AMOUNT, startPos.y + proj.getVelocity().y * TIME_SKIP_AMOUNT);
                //BaB has to be moved in a special way, to avoid stretching issues. Notably, they should just disappear if they have no source
                if (proj.getSpawnType() == ProjectileSpawnType.BALLISTIC_AS_BEAM) {
                    if (proj.getWeapon() == null || proj.getSource() == null) {
                        engine.removeEntity(proj);
                    } else {
                        String spawnID = proj.getWeapon().getSpec().getWeaponId();
                        float spawnRangeReduction = MathUtils.getDistance(proj.getSource().getLocation(), proj.getLocation());

                        //Spawn, while reducing origin ship's range temporarily
                        proj.getSource().getMutableStats().getEnergyWeaponRangeBonus().modifyFlat("VASS_SUPERTEMP_E_BONUS", -spawnRangeReduction);
                        proj.getSource().getMutableStats().getBallisticWeaponRangeBonus().modifyFlat("VASS_SUPERTEMP_B_BONUS", -spawnRangeReduction);
                        DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(proj.getSource(), proj.getWeapon(), spawnID, destPos,
                                proj.getFacing(), proj.getSource().getVelocity());
                        proj.getSource().getMutableStats().getEnergyWeaponRangeBonus().unmodify("VASS_SUPERTEMP_E_BONUS");
                        proj.getSource().getMutableStats().getBallisticWeaponRangeBonus().unmodify("VASS_SUPERTEMP_B_BONUS");

                        //Finally, remove the original projectile altogether (and apply special effects on the new projectile)
                        applySpecialEffectOnProj(newProj, true);
                        engine.removeEntity(proj);
                    }
                } else {
                    proj.getLocation().x = destPos.x;
                    proj.getLocation().y = destPos.y;
                }
                if (proj instanceof MissileAPI) {((MissileAPI) proj).setFlightTime(((MissileAPI) proj).getFlightTime() + TIME_SKIP_AMOUNT);}

                //Spawn a fancy particle trail from current position to future position
                List<Vector2f> pointsToSpawnAt = VassUtils.getFancyArcPoints(startPos, destPos,
                        MathUtils.getDistance(startPos, destPos) * MathUtils.getRandomNumberInRange(-0.08f, 0.08f),
                        (int)(MathUtils.getDistance(startPos, destPos) * 0.12));
                Color colorToUse = new Color(255, 210, 180, Math.min((int)(proj.getDamageAmount()*4f), 255));
                if (proj.getDamageType() == DamageType.ENERGY) {
                    colorToUse = new Color(170, 150, 255, Math.min((int)(proj.getDamageAmount()*7f), 255));
                } else if (proj.getDamageType() == DamageType.HIGH_EXPLOSIVE) {
                    colorToUse = new Color(255, 140, 120, Math.min((int)(proj.getDamageAmount()*10f), 255));
                } else if (proj.getDamageType() == DamageType.KINETIC) {
                    colorToUse = new Color(230, 240, 255, Math.min((int)(proj.getDamageAmount()*7f), 255));
                }

                //TODO: improve the visuals of this thing
                for (Vector2f point : pointsToSpawnAt) {
                    engine.addSmoothParticle(point, new Vector2f(0f, 0f), MathUtils.getRandomNumberInRange(0.7f, 1.2f) * (float)Math.sqrt(proj.getDamageAmount()*2f),
                            1f, MathUtils.getRandomNumberInRange(0.2f, 0.4f), colorToUse);
                }

                //Finally, apply special effects for modded weapons that need it
                applySpecialEffectOnProj(proj, false);
            }

            //We also need to play the old loop sounds REALLY quietly to bypass some vanilla sound mixing thingamajigs
            //...I don't know what I should react to the most: the fact that my comments include "thingamajigies" or the fact that my spellchecker auto-corrected it to "thingamajigs"
            Global.getSoundPlayer().playLoop("vass_chrono_jump_start", ship, 1f, 0.00001f, ship.getLocation(), ship.getVelocity());
            Global.getSoundPlayer().playLoop("vass_chrono_jump_loop", ship, 1f, 0.00001f, ship.getLocation(), ship.getVelocity());

            //And play the new effect sound, of course!
            Global.getSoundPlayer().playSound("vass_chrono_jump_end", 1f, 1f, ship.getLocation(), ship.getVelocity());


            //...and remove our post-processing
            if (!hasResetPostProcess) {
                PostProcessShader.resetDefaults();
                hasResetPostProcess = true;
            }
        }

        //If neither case happens, we still want rid of the percieved time mult and post-processing
        else {
            if (!hasResetPostProcess) {
                PostProcessShader.resetDefaults();
                hasResetPostProcess = true;
            }
            engine.getTimeMult().unmodify(id);
        }

        //If we've passed our maximum active duration, de-activate the system at the end of frame
        if (currentActiveDuration > MAX_ACTIVE_DURATION) {
            ship.useSystem();
        }
    }


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
        hasPlayedSound = false;
        if (!hasResetPostProcess) {
            PostProcessShader.resetDefaults();
            hasResetPostProcess = true;
        }
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            if (!state.equals(State.OUT)) {
                if (currentActiveDuration < MIN_ACTIVE_DURATION * 0.33f) {
                    return new StatusData("Priming system.", true);
                } else if (currentActiveDuration < MIN_ACTIVE_DURATION * 0.66f) {
                    return new StatusData("Priming system..", true);
                } else if (currentActiveDuration < MIN_ACTIVE_DURATION) {
                    return new StatusData("Priming system...", true);
                } else if (currentActiveDuration < MAX_ACTIVE_DURATION *0.75f) {
                    return new StatusData("Priming system...DONE", false);
                } else {
                    return new StatusData("ENERGY LEVELS CRITICAL", true);
                }
            } else {
                return new StatusData("ACTIVATE", false);
            }
        }
        return null;
    }


    //If the system isn't ready arming yet, don't allow it to be used
    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (currentActiveDuration < MIN_ACTIVE_DURATION && system.isActive()) {
            return false;
        }

        return super.isUsable(system, ship);
    }

    //Uility function for spawning a visual indicator indicating a projectile's future. Varies slightly based
    //on damage type and amount
    private void spawnFutureIndicator (DamagingProjectileAPI proj) {
        SpriteAPI spriteToRender = Global.getSettings().getSprite("vass_fx", "projectile_jump_indicator");
        Vector2f renderLoc = new Vector2f(proj.getLocation().x + proj.getVelocity().x * TIME_SKIP_AMOUNT, proj.getLocation().y + proj.getVelocity().y * TIME_SKIP_AMOUNT);
        float renderWidth = (float)Math.sqrt(proj.getDamageAmount());
        float renderLength = proj.getVelocity().length() * 0.3f;
        Color colorToUse = new Color(255, 210, 180, Math.min((int)(proj.getDamageAmount()*0.5f), 255));
        if (proj.getDamageType() == DamageType.ENERGY) {
            colorToUse = new Color(170, 150, 255, Math.min((int)(proj.getDamageAmount()*0.8f), 255));
        } else if (proj.getDamageType() == DamageType.HIGH_EXPLOSIVE) {
            colorToUse = new Color(255, 140, 120, Math.min((int)(proj.getDamageAmount()*1.2f), 255));
        } else if (proj.getDamageType() == DamageType.KINETIC) {
            colorToUse = new Color(230, 240, 255, Math.min((int)(proj.getDamageAmount()*0.7f), 255));
        }
        MagicRender.singleframe(spriteToRender, renderLoc, new Vector2f(renderWidth, renderLength),
                VectorUtils.getAngle(Misc.ZERO, proj.getVelocity())-90f, colorToUse,
                true, CombatEngineLayers.ABOVE_SHIPS_LAYER);
    }


    //Applies special effects for specific modded scripted weapons to ensure compatibility mostly staying
    private void applySpecialEffectOnProj(DamagingProjectileAPI proj, boolean fromNewlySpawnedProjectile) {
        if (proj == null || proj.getProjectileSpecId() == null) {
            return;
        }
        if (proj.getProjectileSpecId().equals("vass_fragarach_shot")) {
            Global.getCombatEngine().addPlugin(new VassTimeSplitProjScript(proj, VassFragarachScript.GRACE_DISTANCE, VassFragarachScript.GRACE_DAMAGE_MULT));
        }
    }
}