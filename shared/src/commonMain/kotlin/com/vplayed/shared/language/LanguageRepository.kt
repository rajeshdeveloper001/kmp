package com.vplayed.shared.language

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.flatMap
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.requireResponse
import kotlinx.serialization.json.JsonArray

/** The language list (`GET mediaview/api/v1/language`). Every non-empty load is kept in the graph's [SupportedLanguagesStore]. */
interface LanguageRepository {
    suspend fun load(): DataResult<List<Language>>
}

internal class DefaultLanguageRepository(
    private val api: ApiCaller,
    /** Receives every successfully loaded, non-empty list. */
    private val store: SupportedLanguagesStore = SupportedLanguagesStore(),
) : LanguageRepository {

    override suspend fun load(): DataResult<List<Language>> =
        api.get(PATH).flatMap { it.requireResponse() }.flatMap { response ->
            // Entries without a code are skipped, like the other lists the apps read.
            val languages = (response["data"] as? JsonArray).orEmpty().mapNotNull(Language::from).distinctBy { it.code }
            if (languages.isEmpty()) DataResult.Failure(AppError.Parsing) else DataResult.Success(languages)
        }.also { result -> if (result is DataResult.Success) store.update(result.data) }

    private companion object {
        const val PATH = "mediaview/api/v1/language"
    }
}
