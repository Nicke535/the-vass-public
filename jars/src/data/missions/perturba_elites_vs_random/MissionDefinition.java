package data.missions.perturba_elites_vs_random;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.VassBaseRandomMissionDefinition;

import java.util.ArrayList;
import java.util.List;

public class MissionDefinition extends VassBaseRandomMissionDefinition
{
	@Override
	public void defineMission(MissionDefinitionAPI api)
	{
		chooseFactions("vass_perturba", null);
		super.defineMission(api);
	}

	@Override
	protected List<String> getForcedPlayerHullmods() {
		List<String> hullmods = new ArrayList<>();
		hullmods.add("vass_dummymod_perturba_membership");
		return hullmods;
	}
}
