package com.tb24.discordbot.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.tb24.discordbot.L10N
import com.tb24.discordbot.util.Utils
import com.tb24.discordbot.util.await
import com.tb24.discordbot.util.exec
import com.tb24.discordbot.webcampaign.WebCampaign
import com.tb24.fn.util.getInt
import com.tb24.fn.util.getLong
import com.tb24.fn.util.getString
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.TimeFormat
import java.util.concurrent.CompletableFuture

class WebCampaignCommand(name: String, description: String, val domainName: String, aliases: Array<String> = emptyArray()) : BrigadierCommand(name, description, aliases) {
	override fun getNode(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> = newRootNode()
		.executes { execute(it.source) }

	private fun execute(source: CommandSourceStack): Int {
		source.ensureSession()
		source.loading("Connecting")
		source.api.accountService.verify(false).exec()
		val webCampaign = source.session.getWebCampaignManager(domainName)
		CompletableFuture.allOf(
			webCampaign.send("competition.get"),
			webCampaign.send("participant.get"),
		).await()
		val embed = source.createEmbed()
			.setTitle(webCampaign.localization.getString("Metadata.open.title"))
		val codename = webCampaign.environment.getString("name", "").substringAfterLast('-')
		val competition = webCampaign.states["fortnite:epic.${codename}Competition"]!!
		val participant = webCampaign.states[source.api.currentLoggedIn.id + ":epic.participant"]!!
		webCampaign.disconnect()
		val rewards = webCampaign.rewards.groupBy { it.type }

		// Registration
		/*val registrationReward = rewards[WebCampaign.RewardType.REGISTRATION]?.firstOrNull()
		if (registrationReward != null) {
			// TODO Implement registration reward
		}*/

		// Days
		var hasReachedMilestone = false
		competition.getAsJsonArray("days").forEachIndexed { i, day_ ->
			val day = day_.asJsonObject
			val participantDay = participant.getAsJsonArray("dayProgresses").firstOrNull { it.asJsonObject.get("dayId").asString == day.getString("dayId") }?.asJsonObject
			val statsPerPoint = day.getInt("statsPerPoint")
			val max = day.getInt("completionThreshold") * statsPerPoint
			val total = participantDay?.getInt("stats") ?: 0
			val current = total.coerceAtMost(max)
			val statName = day.getString("statName", "")
			val objectiveText = L10N.format("web_campaign.stat.${statName.toLowerCase()}.obj_name")
			val startsAt = day.getLong("startsAt")
			val endsAt = day.getLong("endsAt")
			val hasStarted = System.currentTimeMillis() >= startsAt
			val hasEnded = System.currentTimeMillis() >= endsAt
			val reward = rewards[WebCampaign.RewardType.DAY]!![i]
			var name = ""
			var value = ""
			if (hasStarted) {
				var progressText = "**%,d/%,d** %s".format(current, max, objectiveText)
				if (current >= max) {
					if (current > max) progressText += " \u00b7 Total: **%,d**".format(total)
					hasReachedMilestone = true
				}
				participantDay?.getLong("lastUpdatedAt")?.let {
					progressText += " \u00b7 " + TimeFormat.RELATIVE.format(it)
				}
				val rewardText = (if (current >= statsPerPoint) "✅" else "❌") + ' ' + reward.name
				if (hasEnded) {
					name = "🔴 "
				} else {
					name = "🟢 "
					value = "`${Utils.progress(current, max, 32)}`\n"
				}
				value += "$progressText\n$rewardText"
			} else {
				value = "Reward: " + reward.name
			}
			name += "Day %d".format(i + 1)
			if (!hasStarted) {
				name += ' ' + TimeFormat.RELATIVE.format(startsAt)
			}
			embed.addField(name, value, false)
		}

		// Milestone
		val milestoneReward = rewards[WebCampaign.RewardType.MILESTONE]?.firstOrNull()
		if (milestoneReward != null) {
			embed.addField(webCampaign.localization.getString("RewardCarousel.${milestoneReward.index + 1}.title"), (if (hasReachedMilestone) "✅" else "❌") + ' ' + milestoneReward.name, false)
		}

		val buttons = mutableListOf<Button>()
		//buttons.add(Button.secondary("checkStats", "Check my stats")) // participant.checkStats
		buttons.add(Button.link("https://${webCampaign.domainName}.fortnite.com/assets/languages/en-US/terms.pdf", "Terms of Participation"))

		source.complete(null, embed.build(), ActionRow.of(buttons))
		return Command.SINGLE_SUCCESS
	}
}