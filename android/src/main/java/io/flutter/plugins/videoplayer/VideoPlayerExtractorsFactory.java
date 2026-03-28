// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.mp4.Mp4Extractor;

/**
 * Provides a configured {@link ExtractorsFactory} that ignores MP4/fMP4 edit lists.
 *
 * <p>Some MP4/fMP4 files contain edit lists ({@code elst} atoms) that remap the media timeline.
 * ExoPlayer's partial edit-list support can cause incorrect duration reporting or unexpected
 * media skipping/repeating. Setting {@code FLAG_WORKAROUND_IGNORE_EDIT_LISTS} on both {@link
 * Mp4Extractor} and {@link FragmentedMp4Extractor} forces the extractors to ignore these lists
 * and use the raw sample timeline instead.
 */
@OptIn(markerClass = UnstableApi.class)
final class VideoPlayerExtractorsFactory {

  private VideoPlayerExtractorsFactory() {}

  /**
   * Creates a {@link DefaultExtractorsFactory} configured with edit-list workarounds for MP4 and
   * fragmented MP4 files.
   */
  @NonNull
  static ExtractorsFactory create() {
    return new DefaultExtractorsFactory()
        .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        .setFragmentedMp4ExtractorFlags(
            FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS);
  }
}
