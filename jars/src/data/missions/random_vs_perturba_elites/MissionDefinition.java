package data.missions.random_vs_perturba_elites;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.VassBaseRandomMissionDefinition;

import java.util.ArrayList;
import java.util.List;

public class MissionDefinition extends VassBaseRandomMissionDefinition
{
	@Override
	public void defineMission(MissionDefinitionAPI api)
	{
		chooseFactions(null, "vass_perturba");
		super.defineMission(api);
	}

	@Override
	protected List<String> getForcedEnemyHullmods() {
		List<String> hullmods = new ArrayList<>();
		hullmods.add("vass_dummymod_perturba_membership");
		return hullmods;
	}
}
