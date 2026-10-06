package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.SortMode
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * 在全部已导入漫画中均匀随机取一部（D5）；空库返回 failure。
 * [random] 可注入以便测试确定性。
 */
class RandomComicUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke(random: Random = Random.Default): Result<ComicKey> {
        val comics = libraryRepository.observeComics(SortMode.NAME).first()
        if (comics.isEmpty()) {
            return Result.failure(NoSuchElementException("书库为空，请先导入漫画"))
        }
        val index = random.nextInt(comics.size)
        return Result.success(comics[index].key)
    }
}
