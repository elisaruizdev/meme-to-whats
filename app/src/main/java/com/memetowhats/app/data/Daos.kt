package com.memetowhats.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<LibraryItem?>

    @Insert
    suspend fun insert(item: LibraryItem): Long

    @Query(
        "UPDATE items SET title = :title, audioText = :audioText, tags = :tags, categoryId = :categoryId " +
            "WHERE id = :id",
    )
    suspend fun updateDetails(id: Long, title: String, audioText: String, tags: String, categoryId: Long?)

    @Query("UPDATE items SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Delete
    suspend fun delete(item: LibraryItem)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Delete
    suspend fun delete(category: Category)
}
