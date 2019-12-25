package data.missions.vass_vs_random;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.VassBaseRandomMissionDefinition;

import java.util.ArrayList;
import java.util.List;

public class MissionDefinition extends VassBaseRandomMissionDefinition
{
	@Override
	public void defineMission(MissionDefinitionAPI api)
	{
		chooseFactions("vass", null);
		super.defineMission(api);
	}
}
