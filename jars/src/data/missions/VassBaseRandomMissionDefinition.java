package data.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import com.fs.starfarer.api.plugins.AutofitPlugin;
import com.fs.starfarer.api.plugins.impl.CoreAutofitPlugin;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

/**
 * Taken pretty much wholeheartedly from the Legacy of Arkgneisis implementation
 * Modified slightly to fit better with Vass implementations
 * @author Gwyvern
 */
public class VassBaseRandomMissionDefinition implements MissionDefinitionPlugin
{
    public static Logger log = Global.getLogger(VassBaseRandomMissionDefinition.class);

    // Sorts by civilian/not-civilian, then hull size, then fleet point cost, then random
    public static final Comparator<FleetMemberAPI> PRIORITY = new Comparator<FleetMemberAPI>()
    {
        // -1 means member1 is first, 1 means member2 is first
        @Override
        public int compare(FleetMemberAPI member1, FleetMemberAPI member2)
        {
            if (!member1.isCivilian()) {
                if (member2.isCivilian()) {
                    return -1;
                }
            }
            else if (!member2.isCivilian()) {
                return 1;
            }

            int sizeCompare = member2.getHullSpec().getHullSize().
                    compareTo(member1.getHullSpec().getHullSize());
            if (sizeCompare != 0) {
                return sizeCompare;
            }

            if (member1.getFleetPointCost() > member2.getFleetPointCost()) {
                return -1;
            }
            else if (member1.getFleetPointCost() < member2.getFleetPointCost()) {
                return 1;
            }

            return MathUtils.getRandomNumberInRange(-1, 1);
        }
    };

    public static final float STATION_CHANCE = 0.25f;
    public static final float STATION_CHANCE_PLAYER = 0.4f; // Decides whether the station should be on player or enemy side

    // Types of objectives that may be randomly used
    protected static final String[] OBJECTIVE_TYPES = {
        "sensor_array", "nav_buoy", "comm_relay"
    };
    protected static final Map<String, Float> QUALITY_FACTORS = new HashMap<>(13);

    protected static final List<String> MISSING_HULLS = new ArrayList<>();

    // Don't fucking forget to update the HashMap number when more factions are added, the other one (ArrayList) lower in the script as well!
    static
    {
        QUALITY_FACTORS.put("default", 0.5f);
        QUALITY_FACTORS.put("al_ars", 0.5f);                // The average of their ships tend to be middle of the road
        QUALITY_FACTORS.put(Factions.DERELICT, 0f);         // Old and worn out von Neumann probes that are are very poorly equipped
        QUALITY_FACTORS.put(Factions.DIKTAT, 0.5f);         // Bog standard dictatorship with average gear
        QUALITY_FACTORS.put(Factions.HEGEMONY, 0.5f);       // Comsec approved average gear
        QUALITY_FACTORS.put(Factions.INDEPENDENT, 0.5f);    // Independents with average gear
        QUALITY_FACTORS.put(Factions.LIONS_GUARD, 0.75f);   // Elite subdivision of the Diktat with above average gear
        QUALITY_FACTORS.put(Factions.LUDDIC_CHURCH, 0.25f); // Luddites are pacifists and poorly equipped
        QUALITY_FACTORS.put(Factions.LUDDIC_PATH, 0f);      // Fanatics who are very poorly equipped
        QUALITY_FACTORS.put(Factions.PERSEAN, 0.55f);       // Space NATO has slightly above average gear
        QUALITY_FACTORS.put(Factions.PIRATES, 0f);          // Criminals who are very poorly equipped
        QUALITY_FACTORS.put(Factions.REMNANTS, 1f);         // Are you Omega? Top of the line gear baby
        QUALITY_FACTORS.put(Factions.TRITACHYON, 0.85f);    // Mega-corp with high-quality gear
        QUALITY_FACTORS.put("blackrock_driveyards", 0.75f); // Esoteric tech-lords with above average gear
        QUALITY_FACTORS.put("diableavionics", 0.75f);       // Slavers with mysterious backers that posses above average gear
        QUALITY_FACTORS.put("exigency", 1f);                // Stay out from under foot or be stepped on
        QUALITY_FACTORS.put("exipirated", 0.55f);           // These pirates have some remarkable technology...
        QUALITY_FACTORS.put("interstellarimperium", 0.6f);  // Well equipped and well disciplined
        QUALITY_FACTORS.put("junk_pirates", 0.45f);         // Janky ships and weapons that are surprisingly effective
        QUALITY_FACTORS.put("pack", 0.5f);                  // Isolationists with effective and unique gear
        QUALITY_FACTORS.put("syndicate_asp", 0.5f);         // Space FedEx is well funded and well armed
        QUALITY_FACTORS.put("templars", 1f);                // What, did aliens give them this shit?
        QUALITY_FACTORS.put("ORA", 0.75f);                  // They found a hell of a cache of ships and weapons
        QUALITY_FACTORS.put("SCY", 0.55f);                  // Well equipped spies and tech-hoarders
        QUALITY_FACTORS.put("shadow_industry", 0.65f);      // Pre-collapse organization that is well equipped
        QUALITY_FACTORS.put("Coalition", 0.65f);            // Well entrenched and equipped coalition
        QUALITY_FACTORS.put("dassault_mikoyan", 0.75f);     // Mega-corp with above average gear
        QUALITY_FACTORS.put("6eme_bureau", 0.85f);          // Elite subdivision of DME with high-quality gear
        QUALITY_FACTORS.put("blade_breakers", 1f);          // Jesus, who developed this tech?
        QUALITY_FACTORS.put("OCI", 0.75f);                  // Anyone who traveled as far as they have is well equipped
        QUALITY_FACTORS.put("tiandong", 0.55f);             // Refits tend to be made with care and have slightly above average gear   
        QUALITY_FACTORS.put("gmda", 0.5f);                  // Space Police with average gear
        QUALITY_FACTORS.put("draco", 0.55f);                // Space Vampire pirates with slightly enhanced tech
        QUALITY_FACTORS.put("fang", 0.5f);                  // Psycho Werewolves with average gear
        QUALITY_FACTORS.put("HMI", 0.5f);                   // Miners and "legitimate" pirates with average gear
        QUALITY_FACTORS.put("mess", 0.9f);                  // Gray goo enhanced ships and weapons
        QUALITY_FACTORS.put("sylphon", 0.75f);              // AI collaborators with advanced tech
        QUALITY_FACTORS.put("fob", 0.8f);                   // Aliens with... Alien tech
        QUALITY_FACTORS.put("vass", 1f);                    // Time Mafia does not settle for "imperfect"
    }

    // True enables fighting against all show-in-intel-tab factions, not just the ones listed above
    protected static boolean testMode = false;
    // If true, ship variants are randomized like in the campaign
    protected static boolean randomMode = false;
    // Adds admiral AI to player side
    protected static boolean useAdmiralAI = false;
    // If false, enemy gets no point advantage over player
    protected static boolean useDifficultyModifier = true;

    protected FactionAPI enemy;
    protected FactionAPI player;
    protected final Random rand = new Random();

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        if (player == null || enemy == null) {
            chooseFactions(null, null);
        }

        api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, useAdmiralAI, 5);
        api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 5);

        api.setFleetTagline(FleetSide.PLAYER, Misc.ucFirst(player.getDisplayNameLong()) + " forces");
        api.setFleetTagline(FleetSide.ENEMY, Misc.ucFirst(enemy.getDisplayNameLong()) + " forces");

        // Fleet size randomization
        int size = 25 + (int) (Math.random() * 225);
        float difficulty = 0.7f + rand.nextFloat() * 0.6f;
        if (!useDifficultyModifier) {
            difficulty = 1;
        }

        // Actual fleet generation call
        int playerFP = generateFleet(player, api, FleetSide.PLAYER, (int) (size * difficulty), getForcedPlayerHullmods());
        int enemyFP = generateFleet(enemy, api, FleetSide.ENEMY, size, getForcedEnemyHullmods());

        // Set up the map
        float width = 13000f + 13000f * (size / 200);
        float height = 13000f + 13000f * (size / 200);
        api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);

        float minX = -width / 2;
        float minY = -height / 2;

        int objectiveCount = (int) Math.floor(size / 40f);
        List<Vector2f> objectives = new ArrayList<>();

        while (objectiveCount > 0) {
            String type = OBJECTIVE_TYPES[rand.nextInt(3)];

            if (objectiveCount == 1) {
                api.addObjective(0, 0, type);
                objectiveCount -= 1;
            }
            else {
                int tries = 0;
                while (true) {
                    boolean allow = true;
                    tries++;
                    if (tries >= 15) {
                        objectiveCount = 0;   // Screw this, we're outta here
                        break;
                    }

                    float theta = (float) (Math.random() * Math.PI);
                    double radius = Math.min(width, height);
                    radius = radius * 0.1 + radius * 0.3 * Math.random();
                    int x = (int) (Math.cos(theta) * radius);
                    int y = (int) (Math.sin(theta) * radius);

                    // Check for distance to existing objectives
                    Vector2f pos = new Vector2f(x, -y);
                    for (Vector2f existing : objectives) {
                        if (MathUtils.isWithinRange(existing, pos, 1500)) {
                            allow = false;
                            break;
                        }
                    }
                    if (allow) {
                        api.addObjective(x, -y, type);
                        api.addObjective(-x, y, type);
                        objectives.add(pos);
                        objectiveCount -= 2;
                        break;
                    }
                }
            }
        }

        // Battle takes place in hyperspace
        api.setHyperspaceMode(true);

        // Show the factions versus and their FP
        String str = player.getDisplayName() + "  (" + playerFP + ")   vs.  " + enemy.getDisplayName() + "  (" + enemyFP + ")";
        if (randomMode) {
            str += " || Randomized variants";
        }
        api.addBriefingItem(str);
        
        String asteroidAndNebulaLine = null;

        // Chance of generating a nebula
        float nebulaChance = MathUtils.getRandomNumberInRange(0, 100);

        // So basically half the time (if less than 50 out of 100)
        if (nebulaChance < 50) {
            // Do regular nebula generation
            float nebulaCount = 10 + (float) Math.random() * 30;
            float nebulaSize = (float) Math.random();

            for (int i = 0; i < nebulaCount; i++) {
                float x = (float) Math.random() * width - width / 2;
                float y = (float) Math.random() * height - height / 2;
                float nebulaRadius = (400f + (float) Math.random() * 1600f) * nebulaSize;
                api.addNebula(x, y, nebulaRadius);
            }

            api.addBriefingItem("Nebulosity:  " + (int) (((nebulaCount * nebulaSize) / 40f) * 100) + "%");

        }
        else {
            // Mention that there is no nebula, this line could be commented out if you don't want this line item added
            asteroidAndNebulaLine = "Nebulosity: N/A";
        }

        // Asteroid generation random chance
        float asteroidChance = MathUtils.getRandomNumberInRange(100, 100);  //Apparently, rocks are illegal in hyperspace

        // If chance is less than 50
        if (asteroidChance < 50) {
            // Do regular asteroid generation
            // Minimum asteroid speed between 15 and 50
            int minAsteroidSpeed = MathUtils.getRandomNumberInRange(15, 50);

            // Asteroid count
            int asteroidCount = size + (int) (size * 4 * Math.pow(Math.random(), 2));

            // Add the asteroid field
            api.addAsteroidField(
                    minX + width * 0.5f, // X
                    minY + height * 0.5f, // Y
                    rand.nextInt(90) - 45 + (rand.nextInt() % 2) * 180, // Angle
                    width * 0.25f + ((float) Math.random() * height / 2), // Width
                    minAsteroidSpeed, // Min speed
                    minAsteroidSpeed * 1.1f, // Max speed
                    asteroidCount); // Count

            asteroidAndNebulaLine += "  |  Asteroids:  " + (int) ((asteroidCount / 1000f) * 100) + "% density; speed " + minAsteroidSpeed;
        }
        else {
            // If not asteroid field, specify as N/A, you can comment this out
            asteroidAndNebulaLine += "  |  Asteroids: N/A";
        }
        api.addBriefingItem(asteroidAndNebulaLine);

        
        boolean showLine4 = (!useDifficultyModifier) || useAdmiralAI;
        if (showLine4) {
            List<String> strings = new ArrayList<>();

            if (!useDifficultyModifier)
            {
                strings.add("Difficulty modifier disabled");
            }
            if (useAdmiralAI)
            {
                strings.add("Player admiral AI enabled");
            }

            String newString = "";
            for (int i = 0; i < strings.size(); i++) {
                newString += strings.get(i);

                // Not last item
                if (i < strings.size() - 1) {
                    newString += "  |  ";
                }
            }
            api.addBriefingItem(newString);
        }
    }

    protected float getQuality(FactionAPI faction)
    {
        String id = faction.getId();

        if (QUALITY_FACTORS.containsKey(id))
        {
            return QUALITY_FACTORS.get(id);
        }

        return QUALITY_FACTORS.get("default");
    }

    protected void chooseFactions(String playerFactionId, String enemyFactionId) {
        player = Global.getSector().getFaction(playerFactionId);
        enemy = Global.getSector().getFaction(enemyFactionId);

        List<FactionAPI> acceptableFactions = new ArrayList<>(11);

        // Always use the vanilla factions as valid random factions
        acceptableFactions.add(Global.getSector().getFaction(Factions.DIKTAT));
        acceptableFactions.add(Global.getSector().getFaction(Factions.HEGEMONY));
        acceptableFactions.add(Global.getSector().getFaction(Factions.INDEPENDENT));
        acceptableFactions.add(Global.getSector().getFaction(Factions.LIONS_GUARD));
        acceptableFactions.add(Global.getSector().getFaction(Factions.LUDDIC_CHURCH));
        acceptableFactions.add(Global.getSector().getFaction(Factions.LUDDIC_PATH));
        acceptableFactions.add(Global.getSector().getFaction(Factions.PERSEAN));
        acceptableFactions.add(Global.getSector().getFaction(Factions.PIRATES));
        acceptableFactions.add(Global.getSector().getFaction(Factions.TRITACHYON));

        // Test mode: All factions that appear in intel tab are valid targets
        if (testMode) {
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                // Don't add duplicate entries
                if (acceptableFactions.contains(faction)) {
                    continue;
                }

                // And don't add hidden factions
                if (!faction.isShowInIntelTab()) {
                    continue;
                }
                acceptableFactions.add(faction);
            }
        }

        // If the player faction is not null, and is not specified a parameter in the input, choose from one of the acceptable factions
        // Could be a WeightedRandomPicker instead but bah
        player = player != null ? player : acceptableFactions.get(rand.nextInt(acceptableFactions.size()));

        // Ditto for enemies
        enemy = enemy != null ? enemy : acceptableFactions.get(rand.nextInt(acceptableFactions.size()));
    }

    // Generate a fleet from the campaign fleet generator
    protected int generateFleet(FactionAPI faction, MissionDefinitionAPI api, FleetSide side, int fp, List<String> forcedHullmods) {
        float quality = getQuality(faction);
        FleetParamsV3 params = new FleetParamsV3(
                null, // LocInHyper
                faction.getId(),
                quality,
                FleetTypes.PATROL_LARGE,
                fp, // CombatPts
                0f, // FreighterPts
                0f, // TankerPts
                0f, // TransportPts
                0f, // LinerPts
                0f, // UtilityPts
                0f // QualityMod
        );
        params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        List<FleetMemberAPI> fleetList = fleet.getFleetData().getMembersListCopy();
        Collections.sort(fleetList, PRIORITY); 

        // Randomization stuff
        VassMissionAutofit inflater = null;
        CoreAutofitPlugin auto = null;
        Random random = new Random();

        // Prepare variant randomizer
        if (randomMode) {
            inflater = new VassMissionAutofit(faction);
            auto = new CoreAutofitPlugin(fleet.getCommander());
            auto.setRandom(random);
            auto.setChecked(CoreAutofitPlugin.UPGRADE, true);
            auto.setChecked(CoreAutofitPlugin.RANDOMIZE, true);
        }

        boolean flagshipChosen = false;
        int index = 0;
        for (FleetMemberAPI baseMember : fleetList)
        {
            String variant = baseMember.isFighterWing() ? baseMember.getSpecId() : baseMember.getVariant().getHullVariantId();
            FleetMemberAPI member = api.addToFleet(side, variant, baseMember.getType(), baseMember.getShipName(), (!baseMember.isFighterWing() && !flagshipChosen));
            // Apply randomizer if appropriate
            if (randomMode) {
                randomizeVariant(auto, inflater, member, fleet, faction, index, random);
            }

            if (!baseMember.isFighterWing() && !flagshipChosen) {
                flagshipChosen = true;
            }

            //Ensure that all "forced" hullmods are applied, even if they normally wouldn't fit: we assume they are all hidden hullmods anyhow
            ShipVariantAPI var = member.getVariant().clone();
            for (String forcedHullmod : forcedHullmods) {
                var.addPermaMod(forcedHullmod);
                var.setSource(VariantSource.REFIT);
            }
            member.setVariant(var, true, true);

            index++;
        }

        return fleet.getFleetPoints();
    }

    protected void randomizeVariant(CoreAutofitPlugin auto, AutofitPlugin.AutofitPluginDelegate inflater, FleetMemberAPI member,
            CampaignFleetAPI fleet, FactionAPI faction, int index, Random random) {
        ShipVariantAPI currVariant = Global.getSettings().createEmptyVariant(fleet.getId() + "_" + index, member.getHullSpec());
        ShipVariantAPI target = member.getVariant();

        if (target.isStockVariant()) {
            currVariant.setOriginalVariant(target.getHullVariantId());
        }

        boolean randomize = random.nextFloat() < faction.getDoctrine().getAutofitRandomizeProbability();
        auto.setChecked(CoreAutofitPlugin.RANDOMIZE, randomize);

        auto.doFit(currVariant, target, inflater);
        currVariant.setSource(VariantSource.REFIT);
        member.setVariant(currVariant, false, false);
    }

    //For adding "forced" hullmods to the player side
    protected List<String> getForcedPlayerHullmods() {
        return new ArrayList<>();
    }

    //For adding "forced" hullmods to the enemy side
    protected List<String> getForcedEnemyHullmods() {
        return new ArrayList<>();
    }
}
