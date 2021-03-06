// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.hash;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.UpdatableIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * Main service is used to manage (load / unload / download) shared index chunks.
 */
@ApiStatus.Internal
public interface SharedIndexChunkConfiguration {

  @NotNull
  static SharedIndexChunkConfiguration getInstance() {
    return ServiceManager.getService(SharedIndexChunkConfiguration.class);
  }

  /**
   * Returns index for a given {@param indexId} which are stored in {@param chunkId}.
   */
  @Nullable
  <Value, Key> UpdatableIndex<Key, Value, FileContent> getChunk(@NotNull ID<Key, Value> indexId, int chunkId);

  /**
   * Processes all available shared indexes (among all of loaded chunks) for a given {@param indexId}.
   */
  <Value, Key> void processChunks(@NotNull ID<Key, Value> indexId, @NotNull Processor<UpdatableIndex<Key, Value, FileContent>> processor);

  /**
   * @return - hash_id (internal_hash_id + chunk_id) if {@param hash} is present in some chunk, otherwise returns {@link FileContentHashIndexExtension#NULL_HASH_ID}.
   */
  long tryEnumerateContentHash(byte[] hash) throws IOException;

  /**
   * Sends a request to download or find shared index chunks for a given {@param entries} and then load it.
   */
  void locateIndexes(@NotNull Project project,
                     @NotNull Set<OrderEntry> entries,
                     @NotNull ProgressIndicator indicator);

  boolean attachExistingChunk(int chunkId, @NotNull Project project);
}
