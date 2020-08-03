package data.missions.multa_elites_vs_random;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.VassBaseRandomMissionDefinition;

import java.util.ArrayList;
import java.util.List;

public class MissionDefinition extends VassBaseRandomMissionDefinition
{
	@Override
	public void defineMission(MissionDefinitionAPI api)
	{
		chooseFactions("vass_multa", null);
		super.defineMission(api);
	}

	@Override
	protected List<String> getForcedPlayerHullmods() {
		List<String> hullmods = new ArrayList<>();
		hullmods.add("vass_dummymod_multa_membership");
		return hullmods;
	}
}
