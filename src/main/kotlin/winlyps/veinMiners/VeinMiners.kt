package winlyps.veinMiners

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import java.util.*

class VeinMiners : JavaPlugin(), Listener {

    private val oreTypes = setOf(
        Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, 
        Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE, 
        Material.REDSTONE_ORE, Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE
    )
    
    // Map of ore types to their XP values (approximate values, can be adjusted)
    private val oreExpValues = mapOf(
        Material.COAL_ORE to 0..2,
        Material.DEEPSLATE_COAL_ORE to 0..2,
        Material.IRON_ORE to 0..1,
        Material.DEEPSLATE_IRON_ORE to 0..1,
        Material.COPPER_ORE to 0..2,
        Material.DEEPSLATE_COPPER_ORE to 0..2,
        Material.GOLD_ORE to 0..2,
        Material.DEEPSLATE_GOLD_ORE to 0..2,
        Material.REDSTONE_ORE to 1..5,
        Material.DEEPSLATE_REDSTONE_ORE to 1..5,
        Material.EMERALD_ORE to 3..7,
        Material.DEEPSLATE_EMERALD_ORE to 3..7,
        Material.LAPIS_ORE to 2..5,
        Material.DEEPSLATE_LAPIS_ORE to 2..5,
        Material.DIAMOND_ORE to 3..7,
        Material.DEEPSLATE_DIAMOND_ORE to 3..7,
        Material.NETHER_QUARTZ_ORE to 2..5,
        Material.NETHER_GOLD_ORE to 0..1
    )
    
    private val maxVeinSize = 64 // Limit to prevent excessive mining
    private val random = Random()

    override fun onEnable() {
        // Register events
        server.pluginManager.registerEvents(this, this)
        logger.info("VeinMiners has been enabled!")
    }

    override fun onDisable() {
        logger.info("VeinMiners has been disabled!")
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player
        val tool = player.inventory.itemInMainHand
        
        // Check if the broken block is an ore
        if (block.type in oreTypes) {
            // Use an iterative approach instead of recursive to find and mine connected ore blocks
            val blocksToMine = findConnectedOres(block, block.type)
            
            // Skip the first block as it's already being broken by the event
            val blocksToProcess = blocksToMine.drop(1)
            
            // Process each block in the vein
            for (oreBlock in blocksToProcess) {
                // Create a fake BlockBreakEvent to check if the player can break this block
                val fakeEvent = BlockBreakEvent(oreBlock, player)
                server.pluginManager.callEvent(fakeEvent)
                
                if (!fakeEvent.isCancelled) {
                    // Give experience directly to the player based on ore type
                    if (!hasSilkTouch(tool)) {
                        // In vanilla, Silk Touch prevents XP drops, so only give XP if no Silk Touch
                        givePlayerExperience(player, oreBlock)
                    }
                    
                    // Check if the tool has Silk Touch
                    if (hasSilkTouch(tool)) {
                        // Handle Silk Touch mining
                        val originalType = oreBlock.type
                        
                        // Clear drops to prevent normal drops
                        oreBlock.type = Material.AIR
                        
                        // Drop the block itself (respecting Silk Touch)
                        oreBlock.world.dropItemNaturally(oreBlock.location, ItemStack(originalType))
                    } else {
                        // Normal mining (without Silk Touch)
                        oreBlock.breakNaturally(tool)
                    }
                }
            }
            
            // Log the number of blocks mined for debugging
            logger.info("Vein miner processed ${blocksToMine.size} blocks")
        }
    }
    
    /**
     * Finds all connected ore blocks of the same type using a breadth-first search
     */
    private fun findConnectedOres(startBlock: Block, targetType: Material): List<Block> {
        val visited = HashSet<Block>()
        val queue = LinkedList<Block>()
        val result = ArrayList<Block>()
        
        // Start with the initial block
        queue.add(startBlock)
        visited.add(startBlock)
        result.add(startBlock)
        
        // Process blocks in the queue
        while (queue.isNotEmpty() && result.size < maxVeinSize) {
            val current = queue.poll()
            
            // Check all adjacent blocks (including diagonals)
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        // Skip the center block
                        if (x == 0 && y == 0 && z == 0) continue
                        
                        val adjacentBlock = current.world.getBlockAt(
                            current.x + x,
                            current.y + y,
                            current.z + z
                        )
                        
                        // If this block is of the target type and hasn't been visited yet
                        if (adjacentBlock.type == targetType && adjacentBlock !in visited) {
                            visited.add(adjacentBlock)
                            queue.add(adjacentBlock)
                            result.add(adjacentBlock)
                            
                            // Stop if we've reached the maximum vein size
                            if (result.size >= maxVeinSize) break
                        }
                    }
                    if (result.size >= maxVeinSize) break
                }
                if (result.size >= maxVeinSize) break
            }
        }
        
        return result
    }
    
    /**
     * Gives experience directly to the player based on the ore type
     */
    private fun givePlayerExperience(player: Player, block: Block) {
        // Skip experience if the ore doesn't typically drop XP
        val expRange = oreExpValues[block.type] ?: return
        
        // Calculate random XP amount within the range
        val expAmount = if (expRange.first == expRange.last) {
            expRange.first
        } else {
            random.nextInt(expRange.last - expRange.first + 1) + expRange.first
        }
        
        // Give experience directly to the player
        if (expAmount > 0) {
            player.giveExp(expAmount)
        }
    }
    
    /**
     * Checks if the given item has the Silk Touch enchantment
     */
    private fun hasSilkTouch(item: ItemStack): Boolean {
        if (item.type == Material.AIR) return false
        return item.containsEnchantment(Enchantment.SILK_TOUCH)
    }
}
