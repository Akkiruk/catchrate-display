package com.catchrate

import com.catchrate.platform.PlatformHelper
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CatchRateAuditExport {

    fun saveCatchRateAudit(): String {
        val file = File(getLogsDir(), "catchrate-audit-${fileTimestamp()}.csv")
        file.writeText(buildCsv())
        return file.absolutePath
    }

    private fun buildCsv(): String {
        val speciesList = try {
            PokemonSpecies.species.toList().sortedBy { it.resourceIdentifier.toString() }
        } catch (_: Throwable) {
            emptyList()
        }

        val builder = StringBuilder()
        builder.appendLine(
            listOf(
                "species_id",
                "species_name",
                "form_name",
                "form_showdown_id",
                "resolved_catch_rate",
                "is_estimate",
                "source",
                "source_path",
                "species_registry_catch_rate",
                "form_registry_catch_rate",
                "matches_form_registry"
            ).joinToString(",")
        )

        speciesList.forEach { species ->
            val resolution = SpeciesCatchRateCache.getResolution(species)
            auditForms(species).forEach { form ->
                val matchesFormRegistry = resolution.catchRate == form.catchRate
                builder.appendLine(
                    listOf(
                        csv(species.resourceIdentifier.toString()),
                        csv(species.name),
                        csv(form.name),
                        csv(form.formOnlyShowdownId()),
                        resolution.catchRate.toString(),
                        resolution.isEstimate.toString(),
                        csv(resolution.source),
                        csv(resolution.sourcePath ?: ""),
                        species.catchRate.toString(),
                        form.catchRate.toString(),
                        matchesFormRegistry.toString()
                    ).joinToString(",")
                )
            }
        }

        return builder.toString()
    }

    private fun auditForms(species: Species): List<FormData> {
        val forms = species.forms.ifEmpty { mutableListOf(species.standardForm) }
        return forms.distinctBy { it.name }
    }

    private fun getLogsDir(): File {
        val dir = File(PlatformHelper.getGameDir().toFile(), "catchrate-logs")
        dir.mkdirs()
        return dir
    }

    private fun fileTimestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}