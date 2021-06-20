package me.professortox.protocollibtest;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;

// Code is a bit ugly since it was ported from a larger plugin for isolated testing
public class ProtocolLibTest extends JavaPlugin implements Listener
{
    private HashMap<String, HashMap<BlockPosition, WrappedBlockData>> playerFakeBlocks;
    private HashSet<String> fakeBlockAdd;

    @Override
    public void onEnable()
    {
        this.playerFakeBlocks = new HashMap<>();
        this.fakeBlockAdd = new HashSet<>(4);

        getServer().getPluginManager().registerEvents(this, this);

        ProtocolLibrary.getProtocolManager().getAsynchronousManager().registerAsyncHandler(new PacketAdapter(
                this, ListenerPriority.NORMAL, PacketType.Play.Server.BLOCK_CHANGE)
        {
            // Paper is copying/caching packets to send. this results in player 2 receiving
            // block update sometimes (when they shouldn't) and player 1 receiving the debug message
            @Override
            public void onPacketSending(PacketEvent event)
            {
                if (event.getPacketType().equals(PacketType.Play.Server.BLOCK_CHANGE))
                {
                    HashMap<BlockPosition, WrappedBlockData> fakeBlocks
                            = playerFakeBlocks.get(event.getPlayer().getName()); // O(1)

                    if (fakeBlocks != null)
                    {
                        PacketContainer packet = event.getPacket();
                        BlockPosition location = packet.getBlockPositionModifier().read(0);

                        if (fakeBlocks.containsKey(location)) // O(1)
                        {
                            // Don't cancel packet; instead, override the updated block
                            packet.getBlockData().write(0, fakeBlocks.get(location)); // O(1)
                            event.getPlayer().sendMessage("BLOCK_CHANGE OVERRIDDEN");
                        }
                    }
                }
            }
        }).start();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        playerFakeBlocks.put(event.getPlayer().getName(), new HashMap<>());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        playerFakeBlocks.remove(event.getPlayer().getName());
        playerFakeBlocks.remove(event.getPlayer().getName());
    }

    @EventHandler
    public void onFakeBlockAdd(PlayerInteractEvent event)
    {
        if (fakeBlockAdd.contains(event.getPlayer().getName()))
        {
            final Block block = event.getClickedBlock();

            if (block != null)
            {
                final int x = block.getX();
                final int y = block.getY();
                final int z = block.getZ();

                fakeBlockAdd.remove(event.getPlayer().getName());
                playerFakeBlocks.get(event.getPlayer().getName()).put(new BlockPosition(x, y, z),
                        WrappedBlockData.createData(Material.REDSTONE_BLOCK));

                event.getPlayer().sendMessage("You have added block at coordinates "
                        + "(" + x + ", " + y + ", " + z + ")" + ". It"
                        + " has been set to a redstone block.");

                event.setCancelled(true);
            }
        }
    }

    public boolean enableFakeBlockSelection(Player player)
    {
        return fakeBlockAdd.add(player.getName());
    }

    @EventHandler
    public void onTestCommand(PlayerCommandPreprocessEvent event)
    {
        if (event.getMessage().toLowerCase().startsWith("/fakeblock"))
        {
            if (enableFakeBlockSelection(event.getPlayer()))
                event.getPlayer().sendMessage("You can now select a block to turn into a fake block.");
            else
                event.getPlayer().sendMessage("You can already select a block to turn into a fake block.");

            event.setCancelled(true);
        }
    }
}
