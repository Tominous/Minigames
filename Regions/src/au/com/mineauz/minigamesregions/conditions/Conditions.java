package au.com.mineauz.minigamesregions.conditions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import au.com.mineauz.minigames.MinigamePlayer;
import au.com.mineauz.minigames.MinigameUtils;
import au.com.mineauz.minigames.menu.Menu;
import au.com.mineauz.minigames.menu.MenuItemPage;
import au.com.mineauz.minigamesregions.NodeExecutor;
import au.com.mineauz.minigamesregions.RegionExecutor;
import au.com.mineauz.minigamesregions.menuitems.MenuItemCondition;
import au.com.mineauz.minigamesregions.menuitems.MenuItemConditionAdd;

public class Conditions {
	private static Map<String, Class<? extends ConditionInterface>> conditions = new HashMap<String, Class<? extends ConditionInterface>>();
	
	static{
		addCondition("PLAYER_HEALTH_RANGE", PlayerHealthRangeCondition.class);
		addCondition("HAS_REQUIRED_FLAGS", HasRequiredFlagsCondition.class);
		addCondition("PLAYER_SCORE_RANGE", PlayerScoreRangeCondition.class);
		addCondition("MATCH_TEAM", MatchTeamCondition.class);
		addCondition("CONTAINS_ONE_TEAM", ContainsOneTeamCondition.class);
		addCondition("RANDOM_CHANCE", RandomChanceCondition.class);
		addCondition("MATCH_BLOCK", MatchBlockCondition.class);
		addCondition("CONTAINS_ENTIRE_TEAM", ContainsEntireTeamCondition.class);
		addCondition("PLAYER_COUNT", PlayerCountCondition.class);
		addCondition("PLAYER_HAS_ITEM", PlayerHasItemCondition.class);
		addCondition("TEAM_SCORE_RANGE", TeamScoreRangeCondition.class);
	}
	
	public static void addCondition(String name, Class<? extends ConditionInterface> condition){
		conditions.put(name, condition);
	}
	
	public static boolean hasCondition(String condition){
		return conditions.containsKey(condition.toUpperCase());
	}
	
	public static ConditionInterface getConditionByName(String name){
		if(hasCondition(name.toUpperCase())){
			try{
				return conditions.get(name.toUpperCase()).newInstance();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			};
		}
		return null;
	}
	
	public static Set<String> getAllConditionNames(){
		return conditions.keySet();
	}
	
	public static void displayMenu(MinigamePlayer player, RegionExecutor exec, Menu prev){
		Menu m = new Menu(3, "Conditions", player);
		m.setPreviousPage(prev);
		for(ConditionInterface con : exec.getConditions()){
			m.addItem(new MenuItemCondition(MinigameUtils.capitalize(con.getName()), Material.PAPER, exec, con));
		}
		m.addItem(new MenuItemPage("Back", Material.REDSTONE_TORCH_ON, prev), m.getSize() - 9);
		m.addItem(new MenuItemConditionAdd("Add Condition", Material.ITEM_FRAME, exec), m.getSize() - 1);
		m.displayMenu(player);
	}
	
	public static void displayMenu(MinigamePlayer player, NodeExecutor exec, Menu prev){
		Menu m = new Menu(3, "Conditions", player);
		m.setPreviousPage(prev);
		for(ConditionInterface con : exec.getConditions()){
			m.addItem(new MenuItemCondition(MinigameUtils.capitalize(con.getName()), Material.PAPER, exec, con));
		}
		m.addItem(new MenuItemPage("Back", Material.REDSTONE_TORCH_ON, prev), m.getSize() - 9);
		m.addItem(new MenuItemConditionAdd("Add Condition", Material.ITEM_FRAME, exec), m.getSize() - 1);
		m.displayMenu(player);
	}
}
