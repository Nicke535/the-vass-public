package data.missions.vasstest2;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

public class MissionDefinition implements MissionDefinitionPlugin {

    private List shipsPlayer = new ArrayList();
	private List shipsEnemy = new ArrayList();
    
	private void addShipPlayer(String variant, int weight) {
		for (int i = 0; i < weight; i++) {
			shipsPlayer.add(variant);
		}
	}
    private void addShipEnemy(String variant, int weight) {
        for (int i = 0; i < weight; i++) {
            shipsEnemy.add(variant);
        }
    }
	
	private void generateFleet(int maxFP, FleetSide side, List ships, MissionDefinitionAPI api) {
		int currFP = 0;
		
		if (side == FleetSide.PLAYER) {
			String [] choices = {
					"vass_zhanmadao_standard",
                    "vass_zhanmadao_defensive",
					"vass_curtana_support",
                    "vass_curtana_ex",
					"vass_schiavona_standard"
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
        addShipPlayer("vass_zhanmadao_standard", 2);
        addShipPlayer("vass_zhanmadao_defensive", 2);
        addShipPlayer("vass_curtana_standard", 2);
        addShipPlayer("vass_curtana_ex", 1);
        addShipPlayer("vass_curtana_support", 2);
        addShipPlayer("vass_schiavona_standard", 8);
        addShipPlayer("vass_makhaira_standard", 7);
        addShipPlayer("vass_makhaira_support", 4);

		addShipEnemy("doom_Strike", 3);
		addShipEnemy("shade_Assault", 7);
		addShipEnemy("afflictor_Strike", 7);
		addShipEnemy("hyperion_Attack", 3);
		addShipEnemy("hyperion_Strike", 3);
		addShipEnemy("onslaught_Standard", 3);
		addShipEnemy("onslaught_Outdated", 3);
		addShipEnemy("onslaught_Elite", 1);
		addShipEnemy("astral_Elite", 3);
		addShipEnemy("astral_Strike", 3);
		addShipEnemy("astral_Attack", 3);
		addShipEnemy("paragon_Elite", 1);
		addShipEnemy("legion_Strike", 1);
		addShipEnemy("legion_Assault", 1);
		addShipEnemy("legion_Escort", 1);
		addShipEnemy("legion_FS", 1);
		addShipEnemy("odyssey_Balanced", 2);
		addShipEnemy("conquest_Elite", 3);
		addShipEnemy("eagle_Assault", 5);
		addShipEnemy("falcon_Attack", 5);
		addShipEnemy("venture_Balanced", 5);
		addShipEnemy("apogee_Balanced", 5);
		addShipEnemy("aurora_Balanced", 5);
		addShipEnemy("aurora_Balanced", 5);
		addShipEnemy("mora_Assault", 3);
		addShipEnemy("mora_Strike", 3);
		addShipEnemy("mora_Support", 3);
		addShipEnemy("dominator_Assault", 5);
		addShipEnemy("dominator_Support", 5);
		addShipEnemy("medusa_Attack", 5);
		addShipEnemy("condor_Support", 15);
		addShipEnemy("condor_Strike", 15);
		addShipEnemy("condor_Attack", 15);
		addShipEnemy("enforcer_Assault", 15);
		addShipEnemy("enforcer_CS", 15);
		addShipEnemy("hammerhead_Balanced", 10);
		addShipEnemy("hammerhead_Elite", 5);
		addShipEnemy("drover_Strike", 10);
		addShipEnemy("sunder_CS", 10);
		addShipEnemy("gemini_Standard", 8);
		addShipEnemy("buffalo2_FS", 20);
		addShipEnemy("lasher_CS", 20);
		addShipEnemy("lasher_Standard", 20);
		addShipEnemy("hound_Standard", 15);
		addShipEnemy("tempest_Attack", 15);
		addShipEnemy("brawler_Assault", 15);
		addShipEnemy("wolf_CS", 2);
		addShipEnemy("hyperion_Strike", 1);
		addShipEnemy("vigilance_Standard", 10);
		addShipEnemy("vigilance_FS", 15);
		addShipEnemy("tempest_Attack", 2);
		addShipEnemy("brawler_Assault", 10);
		
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
		generateFleet(100 + (int)((float) Math.random() * 35), FleetSide.PLAYER, shipsPlayer, api);
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

