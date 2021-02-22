package com.tb24.discordbot.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.tb24.discordbot.GridSlot
import com.tb24.discordbot.createAttachmentOfIcons
import com.tb24.discordbot.util.*
import com.tb24.fn.model.FortItemStack
import com.tb24.fn.model.mcpprofile.commands.QueryProfile
import com.tb24.fn.util.Formatters
import com.tb24.fn.util.getPreviewImagePath
import me.fungames.jfortniteparse.ue4.assets.exports.tex.UTexture2D
import me.fungames.jfortniteparse.ue4.converters.textures.toBufferedImage
import net.dv8tion.jda.api.entities.Message
import okhttp3.Request
import java.util.*
import java.util.concurrent.CompletableFuture

class LockerCommand : BrigadierCommand("locker", "Shows your BR locker in form of an image.") {
	override fun getNode(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> = newRootNode()
		.executes { c ->
			val source = c.source
			source.ensureSession()
			source.loading("Getting cosmetics")
			source.api.profileManager.dispatchClientCommandRequest(QueryProfile(), "athena").await()
			val athena = source.api.profileManager.getProfileData("athena")
			val ctgs: Map<String, MutableSet<FortItemStack>> = mapOf(
				"AthenaCharacter" to mutableSetOf(),
				"AthenaBackpack" to mutableSetOf(),
				"AthenaPickaxe" to mutableSetOf(),
				"AthenaGlider" to mutableSetOf(),
				"AthenaSkyDiveContrail" to mutableSetOf(),
				"AthenaDance" to mutableSetOf(),
				"AthenaItemWrap" to mutableSetOf(),
				"AthenaMusicPack" to mutableSetOf(),
				//"AthenaLoadingScreen" to mutableSetOf()
			)
			for (item in athena.items.values) {
				val ids = ctgs[item.primaryAssetType]
				if (ids != null && (item.primaryAssetType != "AthenaDance" || item.primaryAssetName.startsWith("eid_"))) {
					ids.add(item)
				}
			}
			source.loading("Generating and uploading images")
			CompletableFuture.allOf(
				perform(source, "Outfits", ctgs["AthenaCharacter"]),
				perform(source, "Back Blings", ctgs["AthenaBackpack"]),
				perform(source, "Harvesting Tools", ctgs["AthenaPickaxe"]),
				perform(source, "Gliders", ctgs["AthenaGlider"]),
				perform(source, "Contrails", ctgs["AthenaSkyDiveContrail"]),
				perform(source, "Emotes", ctgs["AthenaDance"]),
				perform(source, "Wraps", ctgs["AthenaItemWrap"]),
				perform(source, "Musics", ctgs["AthenaMusicPack"]),
				//perform(source, "Loading Screens", ctgs["AthenaLoadingScreen"])
			).await()
			source.complete("✅ All images have been sent successfully.")
			Command.SINGLE_SUCCESS
		}
}

class ExclusivesCommand : BrigadierCommand("exclusives", "Shows your exclusive cosmetics in an image.") {
	override fun getNode(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> = newRootNode()
		.executes { c ->
			val source = c.source
			source.ensureSession()
			source.loading("Getting cosmetics")
			source.api.profileManager.dispatchClientCommandRequest(QueryProfile(), "athena").await()
			val athena = source.api.profileManager.getProfileData("athena")
			val exclusiveTemplateIds = mutableListOf<String>()
			source.client.okHttpClient.newCall(Request.Builder().url("https://fort-api.com/exclusives/list").build()).exec().body()!!.charStream().forEachLine {
				exclusiveTemplateIds.add(it)
			}
			val items = athena.items.values.filter { item -> exclusiveTemplateIds.any { it.equals(item.templateId, true) } }
			perform(source, "Exclusives", items).await()
			source.loadingMsg!!.delete().queue()
			Command.SINGLE_SUCCESS
		}
}

private fun perform(source: CommandSourceStack, name: String, ids: Collection<FortItemStack>?) = CompletableFuture.supplyAsync {
	if (ids.isNullOrEmpty()) {
		return@supplyAsync null
	}
	val slots = mutableListOf<GridSlot>()
	for (item in ids.sortedWith(SimpleAthenaLockerItemComparator())) {
		val itemData = item.defData ?: return@supplyAsync null
		slots.add(GridSlot(
			image = item.getPreviewImagePath()?.load<UTexture2D>()?.toBufferedImage(),
			name = item.displayName,
			rarity = itemData.Rarity
		))
	}
	var png: ByteArray
	var scale = 1f
	do {
		png = createAttachmentOfIcons(slots, "locker", scale)
		//println("png size ${png.size} scale $scale")
		scale -= 0.2f
	} while (png.size > Message.MAX_FILE_SIZE && scale >= 0.2f)
	source.channel.sendMessage("**$name** (${Formatters.num.format(ids.size)})")
		.addFile(png, "$name-${source.api.currentLoggedIn.id}.png").complete()
}