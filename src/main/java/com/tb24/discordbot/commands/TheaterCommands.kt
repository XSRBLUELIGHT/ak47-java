package com.tb24.discordbot.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.tb24.discordbot.util.exec
import com.tb24.discordbot.util.renderWithIcon
import com.tb24.discordbot.util.textureEmote
import com.tb24.fn.model.assetdata.FortActiveTheaterInfo
import com.tb24.fn.model.assetdata.FortMissionGenerator
import com.tb24.fn.model.assetdata.FortZoneTheme
import com.tb24.fn.model.mcpprofile.McpProfile
import com.tb24.fn.model.mcpprofile.attributes.CampaignProfileAttributes
import com.tb24.fn.util.format
import com.tb24.fn.util.getPathName
import com.tb24.uasset.StructJson
import com.tb24.uasset.loadObject
import me.fungames.jfortniteparse.fort.objects.rows.GameDifficultyInfo
import me.fungames.jfortniteparse.ue4.assets.exports.UClass
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTag
import me.fungames.jfortniteparse.ue4.assets.util.mapToClass

class MtxAlertsCommand : BrigadierCommand("vbucksalerts", "Shows today's V-Bucks alerts.", arrayOf("va", "mtxalerts")) {
	override fun getNode(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> = newRootNode()
		.withPublicProfile(::execute, "Getting mission alerts info")

	private fun execute(c: CommandContext<CommandSourceStack>, campaign: McpProfile): Int {
		val source = c.source
		source.ensureCompletedCampaignTutorial(campaign)
		val canReceiveMtxCurrency = campaign.items.values.any { it.templateId == "Token:receivemtxcurrency" }
		val attrs = campaign.stats.attributes as CampaignProfileAttributes
		val response = source.api.fortniteService.queryTheaterList("en").exec().body()!!.charStream()
			.use { StructJson.GSON.fromJson(it, FortActiveTheaterInfo::class.java) }
		var totalMtx = 0
		val embed = source.createEmbed(campaign.owner)
		for (theater in response.Theaters) {
			val missions = response.getAvailableMissions(theater)
			val missionAlerts = response.getMissionAlerts(theater)
			for (mission in missions.AvailableMissions) {
				val missionAlert = missionAlerts.getMissionAlert(mission)
				val tile = theater.Tiles[mission.TileIndex]
				val mtxLoot = missionAlert?.MissionAlertRewards?.items?.firstOrNull { it.itemType == "AccountResource:currency_mtxswap" }
					?: continue
				totalMtx += mtxLoot.quantity
				val difficulty = mission.MissionDifficultyInfo.getRowMapped<GameDifficultyInfo>()
				val missionGenerator = loadCDO(mission.MissionGenerator.toString(), FortMissionGenerator::class.java)
				val zoneTheme = loadCDO(tile.ZoneTheme.toString(), FortZoneTheme::class.java)
				val hasCompletedMissionAlert = attrs.mission_alert_redemption_record?.claimData?.any { it.missionAlertId == missionAlert.MissionAlertGuid } == true

				val strike = if (hasCompletedMissionAlert) "~~" else ""
				val title = "%s[%,d] %s %s%s%s".format(
					strike,
					difficulty.RecommendedRating,
					textureEmote(missionGenerator?.MissionIcon?.ResourceObject?.getPathName())?.asMention,
					missionGenerator?.MissionName?.format() ?: mission.MissionGenerator.toString().substringAfterLast('.'),
					strike,
					if (hasCompletedMissionAlert) " ✅" else ""
				)
				val sb = StringBuilder()
				sb.append(zoneTheme?.ZoneName?.format() ?: tile.ZoneTheme.toString().substringAfterLast('.'))
				sb.append(" - ${theater.DisplayName.format()}")
				//if (missionAlert != null) {
				//sb.append('\n').append("__Mission alert rewards__")
				missionAlert.MissionAlertRewards.items.forEach {
					sb.append('\n').append(it.asItemStack().apply { setConditionForConditionalItem(canReceiveMtxCurrency) }.renderWithIcon())
				}
				//}
				embed.addField(title, sb.toString(), false)
			}
		}
		if (embed.fields.isEmpty()) {
			embed.setDescription("There are no V-Bucks mission alerts today :(")
		} else {
			embed.setFooter("%,d V-Bucks today".format(totalMtx))
		}
		source.complete(null, embed.build())
		return Command.SINGLE_SUCCESS
	}

	private fun <T : UObject> loadCDO(path: String, clazz: Class<T>): T? {
		val classObject = loadObject<UClass>(path)
			?: return null
		val cdo = classObject.classDefaultObject.load<UObject>()
			?: return null
		val props = mutableListOf<FPropertyTag>()
		getAllProperties(cdo, props)
		return mapToClass(props, clazz)
	}

	private fun getAllProperties(obj: UObject, props: MutableList<FPropertyTag>) {
		obj.template?.value?.let {
			getAllProperties(it, props)
		}
		for (prop in obj.properties) {
			val existing = props.firstOrNull { it.name == prop.name && it.arrayIndex == prop.arrayIndex }
			if (existing != null) {
				existing.prop = prop.prop
			} else {
				props.add(prop)
			}
		}
	}
}