package data.missions.vasstestDEV;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

public class MissionDefinition implements MissionDefinitionPlugin {

    private List shipsEnemy = new ArrayList();
    private List shipsPlayer = new ArrayList();

    private void addShipEnemy(String variant, int weight) {
        for (int i = 0; i < weight; i++) {
            shipsEnemy.add(variant);
        }
    }
    private void addShipPlayer(String variant, int weight) {
        for (int i = 0; i < weight; i++) {
            shipsPlayer.add(variant);
        }
    }

    private void generateFleet(int maxFP, FleetSide side, List ships, MissionDefinitionAPI api) {
        int currFP = 0;

        if (side == FleetSide.PLAYER) {
            String [] choices = {
                    "onslaught_Elite",
                    "astral_Strike",
                    "paragon_Elite",
                    "odyssey_Balanced",
                    "legion_Strike",
                    "legion_FS",
                    "doom_Strike"
            };
            String flagship = choices[(int) (Math.random() * (float) choices.length)];
            api.addToFleet(side, flagship, FleetMemberType.SHIP, true);
            currFP += api.getFleetPointCost(flagship);

            while (true) {
                int index = (int)(Math.random() * shipsPlayer.size());
                String id = (String) shipsPlayer.get(index);
                currFP += api.getFleetPointCost(id);
                if (currFP > maxFP) {
                    return;
                }
                api.addToFleet(side, id, FleetMemberType.SHIP, false);
            }
        } else {
            while (true) {
                int index = (int)(Math.random() * shipsEnemy.size());
                String id = (String) shipsEnemy.get(index);
                currFP += api.getFleetPointCost(id);
                if (currFP > maxFP) {
                    return;
                }
                api.addToFleet(side, id, FleetMemberType.SHIP, false);
            }
        }
    }

    public void defineMission(MissionDefinitionAPI api) {
        addShipEnemy("vass_zhanmadao_standard", 2);
        addShipEnemy("vass_zhanmadao_defensive", 2);
        addShipEnemy("vass_curtana_standard", 2);
        addShipEnemy("vass_curtana_ex", 1);
        addShipEnemy("vass_curtana_support", 2);
        addShipEnemy("vass_schiavona_standard", 8);
        addShipEnemy("vass_makhaira_standard", 7);
        addShipEnemy("vass_makhaira_support", 4);

        // Set up the fleets so we can add ships and fighter wings to them.
        // In this scenario, the fleets are attacking each other, but
        // in other scenarios, a fleet may be defending or trying to escape
        api.initFleet(FleetSide.PLAYER, "VASS", FleetGoal.ATTACK, false, 5);
        api.initFleet(FleetSide.ENEMY, "ISS", FleetGoal.ATTACK, true, 5);

        // Set a small blurb for each fleet that shows up on the mission detail and
        // mission results screens to identify each side.
        api.setFleetTagline(FleetSide.PLAYER, "Vass");
        api.setFleetTagline(FleetSide.ENEMY, "Enemy forces");

        // These show up as items in the bulleted list under
        // "Tactical Objectives" on the mission detail screen
        api.addBriefingItem("Defeat all enemy forces");

        // Set up the fleets
        api.addToFleet(FleetSide.PLAYER, "vass_zhanmadao_standard", FleetMemberType.SHIP, true);
        api.addToFleet(FleetSide.PLAYER, "vass_curtana_standard", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.PLAYER, "vass_schiavona_standard", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.PLAYER, "vass_schiavona_elite_chase", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.PLAYER, "vass_makhaira_standard", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.PLAYER, "vass_javatest_standard", FleetMemberType.SHIP, false);
		
        generateFleet(100 + (int)((float) Math.random() * 35), FleetSide.ENEMY, shipsEnemy, api);

        // Set up the map.
        float width = 24000f;
        float height = 18000f;
        api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);

        float minX = -width/2;
        float minY = -height/2;


        for (int i = 0; i < 50; i++) {
            float x = (float) Math.random() * width - width/2;
            float y = (float) Math.random() * height - height/2;
            float radius = 100f + (float) Math.random() * 400f;
            api.addNebula(x, y, radius);
        }

        // Add objectives
        api.addObjective(minX + width * 0.25f + 2000, minY + height * 0.25f + 2000, "nav_buoy");
        api.addObjective(minX + width * 0.75f - 2000, minY + height * 0.25f + 2000, "comm_relay");
        api.addObjective(minX + width * 0.75f - 2000, minY + height * 0.75f - 2000, "nav_buoy");
        api.addObjective(minX + width * 0.25f + 2000, minY + height * 0.75f - 2000, "comm_relay");
        api.addObjective(minX + width * 0.5f, minY + height * 0.5f, "sensor_array");

        String [] planets = {"barren", "terran", "gas_giant", "ice_giant", "cryovolcanic", "frozen", "jungle", "desert", "arid"};
        String planet = planets[(int) (Math.random() * (double) planets.length)];
        float radius = 100f + (float) Math.random() * 150f;
        api.addPlanet(0, 0, radius, planet, 200f, true);
    }

}

