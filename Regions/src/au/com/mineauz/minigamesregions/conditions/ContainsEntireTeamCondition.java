package au.com.mineauz.minigamesregions.conditions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;

import au.com.mineauz.minigames.MinigamePlayer;
import au.com.mineauz.minigames.menu.Menu;
import au.com.mineauz.minigamesregions.Node;
import au.com.mineauz.minigamesregions.Region;

public class ContainsEntireTeamCondition extends ConditionInterface {

	@Override
	public String getName() {
		return "CONTAINS_ENTIRE_TEAM";
	}
	
	@Override
	public String getCategory(){
		return "Team Conditions";
	}

	@Override
	public boolean useInRegions() {
		return true;
	}

	@Override
	public boolean useInNodes() {
		return false;
	}

	@Override
	public boolean checkRegionCondition(MinigamePlayer player, Region region, Event event) {
		if(region.getPlayers().containsAll(player.getTeam().getPlayers()))
			return true;
		return false;
	}

	@Override
	public boolean checkNodeCondition(MinigamePlayer player, Node node, Event event) {
		return false;
	}

	@Override
	public void saveArguments(FileConfiguration config, String path) {
	}

	@Override
	public void loadArguments(FileConfiguration config,
			String path) {
		
	}

	@Override
	public boolean displayMenu(MinigamePlayer player, Menu prev) {
		return false;
	}

}
