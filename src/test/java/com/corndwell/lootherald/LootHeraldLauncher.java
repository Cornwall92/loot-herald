package com.corndwell.lootherald;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
public class LootHeraldLauncher {
 public static void main(String[] args) throws Exception {
  ExternalPluginManager.loadBuiltin(LootHeraldPlugin.class);
  RuneLite.main(args);
 }
}
